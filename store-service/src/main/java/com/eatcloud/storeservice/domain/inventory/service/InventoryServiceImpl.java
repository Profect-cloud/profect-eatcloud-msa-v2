package com.eatcloud.storeservice.domain.inventory.service;

import ch.qos.logback.classic.Logger;
import com.eatcloud.storeservice.domain.inventory.StockEvents;
import com.eatcloud.storeservice.domain.inventory.entity.InventoryReservation;
import com.eatcloud.storeservice.domain.inventory.hotpath.HotPathLuaService;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryReservationRepository;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryStockRepository;
import com.eatcloud.storeservice.domain.outbox.service.OutboxAppender;
import com.eatcloud.storeservice.support.lock.RedisLockExecutor;
import com.esotericsoftware.minlog.Log;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RScript;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.redisson.api.RedissonClient;
import org.redisson.api.RBucket;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.eatcloud.storeservice.domain.inventory.hot.HotKeyDecider;
import lombok.extern.slf4j.Slf4j;

import static com.eatcloud.storeservice.support.lock.RedisLockExecutor.LockTimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryStockRepository stockRepo;
    private final InventoryReservationRepository resRepo;
    private final OutboxAppender outbox;
    private final RedisLockExecutor locks;

    // 🔥 Phase B: 핫키 전용 경로 의존성 주입
    private final HotKeyDecider hotKeyDecider;
    private final HotPathLuaService hotPath;
    private final RedissonClient redisson;

    private static final String EVT_RESERVED = "stock.reserved";
    private static final String EVT_RELEASED = "stock.released";
    private static final String EVT_ADJUSTED = "stock.adjusted";

    /** 단일 키를 INCRBY로 증감하되, 음수로 내려가면 롤백 후 -1 반환 */
    private static final String LUA_INCR_SAFELY = ""
            + "local k=KEYS[1]; "
            + "local d=tonumber(ARGV[1]); "
            + "local v=redis.call('INCRBY', k, d); "
            + "if v < 0 then "
            + "  redis.call('INCRBY', k, -d); "
            + "  return -1 "
            + "end "
            + "return v";

    /** 취소: reserved -= qty, avail += qty 를 한 번에. reserved<0 되면 전부 롤백하고 -1 */
    private static final String LUA_CANCEL_SWAP = ""
            + "local a=KEYS[1]; local r=KEYS[2]; "
            + "local q=tonumber(ARGV[1]); "
            + "local rv=redis.call('INCRBY', r, -q); "
            + "if rv < 0 then "
            + "  redis.call('INCRBY', r, q); "
            + "  return -1 "
            + "end "
            + "redis.call('INCRBY', a, q); "
            + "return rv";

    @Override
    @Transactional
    public void reserve(UUID orderId, UUID orderLineId, UUID menuId, int qty) {
        if (hotKeyDecider.isHot(menuId)) {
            log.debug("[HOT] menuId={} flagged as hot, trying Lua path...", menuId);
            try {
                hotPath.reserveViaLua(orderId, orderLineId, menuId, qty);
                return;
            } catch (UnsupportedOperationException e) {
                log.debug("[HOT] Lua not implemented. Fallback to Phase A. menuId={}", menuId);
            } catch (Exception e) {
                log.warn("[HOT] Lua path failed ({}). Fallback to Phase A. menuId={}", e.getMessage(), menuId);
            }
        }

        locks.withMenuLock(menuId.toString(), () -> {
            if (resRepo.findByOrderLineId(orderLineId).isPresent()) return null;

            int updated = stockRepo.reserve(menuId, qty);
            if (updated == 0) throw new InsufficientStockException();

            InventoryReservation r = InventoryReservation.builder()
                    .reservationId(UUID.randomUUID())
                    .menuId(menuId)
                    .orderId(orderId)
                    .orderLineId(orderLineId)
                    .qty(qty)
                    .status("PENDING")
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .createdAt(LocalDateTime.now())
                    .build();
            resRepo.save(r);

            outbox.append(StockEvents.RESERVED, menuId, Map.of(
                    "menuId", menuId, "orderId", orderId, "orderLineId", orderLineId,
                    "qty", qty, "occurredAt", LocalDateTime.now(), "eventVersion", 1
            ));
            return null;
        });
    }

    @Override
    @Transactional
    public void confirm(UUID orderLineId) {
        InventoryReservation r = resRepo.findByOrderLineId(orderLineId)
                .orElseThrow(() -> new IllegalArgumentException("NO_RESERVATION"));

        if (!"PENDING".equals(r.getStatus())) return;

        if (hotKeyDecider.isHot(r.getMenuId())) {
            // 1) DB가 소스오브트루스: reserved 소비
            int u = stockRepo.consume(r.getMenuId(), r.getQty());
            if (u == 0) throw new IllegalStateException("RESERVED_UNDERFLOW");

            // 2) Redis: reserved -= qty (문자열 파싱 없이 원자 수행)
            String reservedKey = "inv:" + r.getMenuId() + ":reserved";
            RScript script = redisson.getScript(StringCodec.INSTANCE);
            Long after = script.eval(
                    RScript.Mode.READ_WRITE,
                    LUA_INCR_SAFELY,
                    RScript.ReturnType.INTEGER,
                    new ArrayList<>(List.of(reservedKey)),
                    String.valueOf(-r.getQty())
            );
            if (after == null || after < 0) throw new IllegalStateException("REDIS_RESERVED_UNDERFLOW");

            r.setStatus("CONFIRMED");
            resRepo.save(r);
            outbox.append("stock.confirmed", r.getMenuId(), Map.of(
                    "menuId", r.getMenuId(), "orderId", r.getOrderId(), "orderLineId", r.getOrderLineId(),
                    "qty", r.getQty(), "occurredAt", LocalDateTime.now(), "eventVersion", 1
            ));
            return;
        }



        // === 비핫키 기존 Phase A 경로 ===
        locks.withMenuLock(r.getMenuId().toString(), () -> {
            int u = stockRepo.consume(r.getMenuId(), r.getQty());
            if (u == 0) throw new IllegalStateException("RESERVED_UNDERFLOW");
            r.setStatus("CONFIRMED");
            resRepo.save(r);
            outbox.append("stock.confirmed", r.getMenuId(), Map.of(/* ... */));
            return null;
        });
    }


    @Override
    @Transactional
    public void cancel(UUID orderLineId, String reason) {
        InventoryReservation r = resRepo.findByOrderLineId(orderLineId)
                .orElseThrow(() -> new IllegalArgumentException("NO_RESERVATION"));

        if (!"PENDING".equals(r.getStatus())) return;

        if (hotKeyDecider.isHot(r.getMenuId())) {
            stockRepo.release(r.getMenuId(), r.getQty()); // DB 복구

            String availKey = "inv:" + r.getMenuId() + ":avail";
            String reservedKey = "inv:" + r.getMenuId() + ":reserved";
            RScript script = redisson.getScript(StringCodec.INSTANCE);
            Long ok = script.eval(
                    RScript.Mode.READ_WRITE,
                    LUA_CANCEL_SWAP,
                    RScript.ReturnType.INTEGER,
                    new ArrayList<>(List.of(availKey, reservedKey)),
                    String.valueOf(r.getQty())
            );
            if (ok == null || ok < 0) throw new IllegalStateException("REDIS_CANCEL_SWAP_FAILED");

            r.setStatus("CANCELED");
            r.setReason(reason);
            resRepo.save(r);
            outbox.append(EVT_RELEASED, r.getMenuId(), Map.of(
                    "menuId", r.getMenuId(), "orderId", r.getOrderId(), "orderLineId", r.getOrderLineId(),
                    "qty", r.getQty(), "reason", reason, "occurredAt", LocalDateTime.now(), "eventVersion", 1
            ));
            return;
        }



        // === 비핫키 기존 Phase A 경로 ===
        locks.withMenuLock(r.getMenuId().toString(), () -> {
            r.setStatus("CANCELED");
            r.setReason(reason);
            resRepo.save(r);

            stockRepo.release(r.getMenuId(), r.getQty()); // DB CAS 복구

            outbox.append(EVT_RELEASED, r.getMenuId(), Map.of(/* ... */));
            return null;
        });
    }


    @Override
    @Transactional
    public void adjust(UUID menuId, int delta) {
        if (hotKeyDecider.isHot(menuId)) {
            int u = stockRepo.adjust(menuId, delta); // DB 먼저
            if (u == 0) throw new IllegalStateException("ADJUST_FAILED");

            String availKey = "inv:" + menuId + ":avail";
            RScript script = redisson.getScript(StringCodec.INSTANCE);
            Long after = script.eval(
                    RScript.Mode.READ_WRITE,
                    LUA_INCR_SAFELY,
                    RScript.ReturnType.INTEGER,
                    new ArrayList<>(List.of(availKey)),
                    String.valueOf(delta)
            );
            if (after == null || after < 0) throw new IllegalStateException("REDIS_AVAIL_UNDERFLOW");

            outbox.append(EVT_ADJUSTED, menuId, Map.of(
                    "menuId", menuId, "delta", delta, "occurredAt", LocalDateTime.now(), "eventVersion", 1
            ));
            return;
        }



        // === 비핫키 기존 Phase A 경로 ===
        locks.withMenuLock(menuId.toString(), () -> {
            int u = stockRepo.adjust(menuId, delta);
            if (u == 0) throw new IllegalStateException("ADJUST_FAILED");
            outbox.append(EVT_ADJUSTED, menuId, Map.of(/* ... */));
            return null;
        });
    }

    // InventoryServiceImpl
    @Override
    @Transactional
    public void cancelAfterConfirm(UUID orderLineId, String reason) {
        var r = resRepo.findByOrderLineId(orderLineId)
                .orElseThrow(() -> new IllegalArgumentException("NO_RESERVATION"));

        // 멱등: 이미 보상 처리된 건은 종료
        if ("REFUNDED".equals(r.getStatus()) || "CANCELED_AFTER_CONFIRM".equals(r.getStatus())) return;

        // 확정 전이면 기존 cancel 로 경로 위임 (멱등 유지)
        if (!"CONFIRMED".equals(r.getStatus())) {
            cancel(orderLineId, reason);
            return;
        }

        // 동일 메뉴 직렬화
        locks.withMenuLock(r.getMenuId().toString(), () -> {
            // DB 재고 복구: available += qty (reserved는 이미 소비됨)
            int u = stockRepo.adjust(r.getMenuId(), +r.getQty());
            if (u == 0) throw new IllegalStateException("ADJUST_FAILED_AFTER_CONFIRM");

            // 🔥 핫키면 Redis write-through
            if (hotKeyDecider.isHot(r.getMenuId())) {
                String availKey = "inv:" + r.getMenuId() + ":avail";
                var avail = redisson.getBucket(availKey, StringCodec.INSTANCE);
                int curAvail = Integer.parseInt((String) avail.get());
                avail.set(String.valueOf(curAvail + r.getQty()));
            }

            // 상태 마킹
            r.setStatus("REFUNDED"); // 또는 "CANCELED_AFTER_CONFIRM"
            r.setReason(reason);
            resRepo.save(r);

            // 이벤트
            outbox.append("stock.returned", r.getMenuId(), Map.of(
                    "menuId", r.getMenuId(),
                    "orderId", r.getOrderId(),
                    "orderLineId", r.getOrderLineId(),
                    "qty", r.getQty(),
                    "reason", reason,
                    "occurredAt", LocalDateTime.now(),
                    "eventVersion", 1
            ));
            return null;
        });
    }



    public static class InsufficientStockException extends RuntimeException {}
    public static class AlreadyProcessedException extends RuntimeException {}

}
