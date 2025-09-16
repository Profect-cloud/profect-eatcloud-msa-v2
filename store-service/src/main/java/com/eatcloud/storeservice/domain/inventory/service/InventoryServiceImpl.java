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

    private static final String AGG_TYPE = "INVENTORY_ITEM";

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
                boolean ok = hotPath.reserveViaLua(orderId, orderLineId, menuId, qty); // ← boolean 반환하도록 권장
                if (!ok) {
                    outbox.append(StockEvents.INSUFFICIENT, AGG_TYPE, menuId,
                            Map.of("menuId", menuId, "orderId", orderId, "orderLineId", orderLineId,
                                    "requestedQty", qty, "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                            Map.of("correlationId", orderId.toString(), "reason", "OUT_OF_STOCK"));
                    return;
                }
                // Lua 성공 시 예약 레코드/DB 상태 처리(이미 hotPath에서 했다면 생략)
                // 필요 시 reserved 이벤트도 여기서 발행
                outbox.append(StockEvents.RESERVED, AGG_TYPE, menuId,
                        Map.of("menuId", menuId, "orderId", orderId, "orderLineId", orderLineId,
                                "qty", qty, "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                        Map.of("correlationId", orderId.toString()));
                return;
            } catch (UnsupportedOperationException e) {
                log.debug("[HOT] Lua not implemented. Fallback to Phase A. menuId={}", menuId);
            } catch (InsufficientStockException e) {
                outbox.append(StockEvents.INSUFFICIENT, AGG_TYPE, menuId,
                        Map.of("menuId", menuId, "orderId", orderId, "orderLineId", orderLineId,
                                "requestedQty", qty, "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                        Map.of("correlationId", orderId.toString(), "reason", "OUT_OF_STOCK"));
                return;
            } catch (Exception e) {
                log.warn("[HOT] Lua path failed ({}). Fallback to Phase A. menuId={}", e.getMessage(), menuId);
            }
        }

        // === 비핫키 기존 Phase A 경로 ===
        locks.withMenuLock(menuId.toString(), () -> {
            if (resRepo.findByOrderLineId(orderLineId).isPresent()) return null;

            int updated = stockRepo.reserve(menuId, qty);
            if (updated == 0) {
                // ✅ 예외 대신 부족 이벤트 발행
                outbox.append(StockEvents.INSUFFICIENT, AGG_TYPE, menuId,
                        Map.of("menuId", menuId, "orderId", orderId, "orderLineId", orderLineId,
                                "requestedQty", qty, "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                        Map.of("correlationId", orderId.toString(), "reason", "OUT_OF_STOCK"));
                return null;
            }

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

            // ✅ outbox 시그니처 변경 반영
            outbox.append(StockEvents.RESERVED, AGG_TYPE, menuId,
                    Map.of("menuId", menuId, "orderId", orderId, "orderLineId", orderLineId,
                            "qty", qty, "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                    Map.of("correlationId", orderId.toString()));
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
            int u = stockRepo.consume(r.getMenuId(), r.getQty());
            if (u == 0) throw new IllegalStateException("RESERVED_UNDERFLOW");

            // Redis reserved -= qty (Lua) 그대로 유지…
            // ... 생략 ...

            r.setStatus("CONFIRMED");
            resRepo.save(r);

            // ✅ confirmed → committed & 시그니처 변경
            outbox.append(StockEvents.COMMITTED, AGG_TYPE, r.getMenuId(),
                    Map.of("menuId", r.getMenuId(), "orderId", r.getOrderId(), "orderLineId", r.getOrderLineId(),
                            "qty", r.getQty(), "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                    Map.of("correlationId", r.getOrderId().toString()));
            return;
        }

        // === 비핫키 경로 ===
        locks.withMenuLock(r.getMenuId().toString(), () -> {
            int u = stockRepo.consume(r.getMenuId(), r.getQty());
            if (u == 0) throw new IllegalStateException("RESERVED_UNDERFLOW");

            r.setStatus("CONFIRMED");
            resRepo.save(r);

            outbox.append(StockEvents.COMMITTED, AGG_TYPE, r.getMenuId(),
                    Map.of("menuId", r.getMenuId(), "orderId", r.getOrderId(), "orderLineId", r.getOrderLineId(),
                            "qty", r.getQty(), "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                    Map.of("correlationId", r.getOrderId().toString()));
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
            stockRepo.release(r.getMenuId(), r.getQty());
            // Redis Lua swap … 유지

            r.setStatus("CANCELED");
            r.setReason(reason);
            resRepo.save(r);

            outbox.append(StockEvents.RELEASED, AGG_TYPE, r.getMenuId(),
                    Map.of("menuId", r.getMenuId(), "orderId", r.getOrderId(), "orderLineId", r.getOrderLineId(),
                            "qty", r.getQty(), "reason", reason, "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                    Map.of("correlationId", r.getOrderId().toString()));
            return;
        }

        // === 비핫키 경로 ===
        locks.withMenuLock(r.getMenuId().toString(), () -> {
            r.setStatus("CANCELED");
            r.setReason(reason);
            resRepo.save(r);

            stockRepo.release(r.getMenuId(), r.getQty());

            outbox.append(StockEvents.RELEASED, AGG_TYPE, r.getMenuId(),
                    Map.of("menuId", r.getMenuId(), "orderId", r.getOrderId(), "orderLineId", r.getOrderLineId(),
                            "qty", r.getQty(), "reason", reason, "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                    Map.of("correlationId", r.getOrderId().toString()));
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

            outbox.append(StockEvents.ADJUSTED, AGG_TYPE, menuId,
                    Map.of("menuId", menuId, "delta", delta, "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                    Map.of("correlationId", "ADMIN-ADJUST"));
            return;
        }



        // === 비핫키 기존 Phase A 경로 ===
        locks.withMenuLock(menuId.toString(), () -> {
            int u = stockRepo.adjust(menuId, delta);
            if (u == 0) throw new IllegalStateException("ADJUST_FAILED");
            outbox.append(StockEvents.ADJUSTED, AGG_TYPE, menuId,
                    Map.of("menuId", menuId, "delta", delta, "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                    Map.of("correlationId", "ADMIN-ADJUST"));
            return null;
        });
    }

    // InventoryServiceImpl

    @Override
    @Transactional
    public void cancelAfterConfirm(UUID orderLineId, String reason) {
        var r = resRepo.findByOrderLineId(orderLineId)
                .orElseThrow(() -> new IllegalArgumentException("NO_RESERVATION"));

        if ("REFUNDED".equals(r.getStatus()) || "CANCELED_AFTER_CONFIRM".equals(r.getStatus())) return;

        if (!"CONFIRMED".equals(r.getStatus())) {
            cancel(orderLineId, reason);
            return;
        }

        locks.withMenuLock(r.getMenuId().toString(), () -> {
            int u = stockRepo.adjust(r.getMenuId(), +r.getQty());
            if (u == 0) throw new IllegalStateException("ADJUST_FAILED_AFTER_CONFIRM");

            if (hotKeyDecider.isHot(r.getMenuId())) {
                String availKey = "inv:" + r.getMenuId() + ":avail";
                var avail = redisson.getBucket(availKey, StringCodec.INSTANCE);
                int curAvail = Integer.parseInt((String) avail.get());
                avail.set(String.valueOf(curAvail + r.getQty()));
            }

            r.setStatus("REFUNDED"); // 또는 "CANCELED_AFTER_CONFIRM"
            r.setReason(reason);
            resRepo.save(r);

            // ✅ 이벤트 표준화 + 신규 시그니처 사용 (aggregateType/headers 포함)
            outbox.append(
                    "stock.returned",              // 표준화: 커밋 이후 반납/환불
                    AGG_TYPE,                      // "INVENTORY_ITEM"
                    r.getMenuId(),
                    Map.of(
                            "menuId",      r.getMenuId(),
                            "orderId",     r.getOrderId(),
                            "orderLineId", r.getOrderLineId(),
                            "qty",         r.getQty(),
                            "reason",      reason,
                            "occurredAt",  LocalDateTime.now(),
                            "eventVersion",1
                    ),
                    Map.of(
                            "correlationId", r.getOrderId().toString()
                    )
            );
            return null;
        });
    }



    public static class InsufficientStockException extends RuntimeException {}
    public static class AlreadyProcessedException extends RuntimeException {}

}
