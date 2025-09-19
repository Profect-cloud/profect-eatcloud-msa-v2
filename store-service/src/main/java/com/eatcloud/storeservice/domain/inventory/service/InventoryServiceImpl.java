package com.eatcloud.storeservice.domain.inventory.service;

import com.eatcloud.storeservice.domain.inventory.StockEvents;
import com.eatcloud.storeservice.domain.inventory.entity.InventoryReservation;
import com.eatcloud.storeservice.domain.inventory.hot.HotKeyDecider;
import com.eatcloud.storeservice.domain.inventory.hotpath.HotPathLuaService;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryReservationRepository;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryStockRepository;
import com.eatcloud.storeservice.domain.outbox.service.OutboxAppender;
import com.eatcloud.storeservice.support.lock.RedisLockExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryStockRepository stockRepo;
    private final InventoryReservationRepository resRepo;
    private final OutboxAppender outbox;
    private final RedisLockExecutor locks;

    // 🔥 Phase B: 핫키 전용 경로
    private final HotKeyDecider hotKeyDecider;
    private final HotPathLuaService hotPath;
    private final RedissonClient redisson;

    // ✅ 이벤트소싱 저장 서비스
    private final StockEventService stockEventService;

    private static final String AGG_TYPE = "INVENTORY_ITEM";

    /** 단일 키를 INCRBY로 증감하되, 음수로 내려가면 롤백 후 -1 반환 */
    private static final String LUA_INCR_SAFELY =
            "local k=KEYS[1]; " +
                    "local d=tonumber(ARGV[1]); " +
                    "local v=redis.call('INCRBY', k, d); " +
                    "if v < 0 then " +
                    "  redis.call('INCRBY', k, -d); " +
                    "  return -1 " +
                    "end " +
                    "return v";

    /** 취소: reserved -= qty, avail += qty 를 한 번에 */
    private static final String LUA_CANCEL_SWAP =
            "local a=KEYS[1]; local r=KEYS[2]; " +
                    "local q=tonumber(ARGV[1]); " +
                    "local rv=redis.call('INCRBY', r, -q); " +
                    "if rv < 0 then " +
                    "  redis.call('INCRBY', r, q); " +
                    "  return -1 " +
                    "end " +
                    "redis.call('INCRBY', a, q); " +
                    "return rv";

    @Override
    @Transactional
    public void reserve(UUID orderId, UUID orderLineId, UUID menuId, int qty) {
        if (hotKeyDecider.isHot(menuId)) {
            log.debug("[HOT] menuId={} flagged as hot, trying Lua path...", menuId);
            try {
                boolean ok = hotPath.reserveViaLua(orderId, orderLineId, menuId, qty);
                if (!ok) {
                    // 이벤트소싱
                    stockEventService.recordOnly(menuId, orderId, orderLineId, "stock.insufficient", qty, "OUT_OF_STOCK");
                    // Outbox
                    outbox.append(StockEvents.INSUFFICIENT, AGG_TYPE, menuId,
                            Map.of("menuId", menuId, "orderId", orderId, "orderLineId", orderLineId,
                                    "requestedQty", qty, "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                            Map.of("correlationId", orderId.toString(), "reason", "OUT_OF_STOCK"));
                    return;
                }
                // 이벤트소싱
                stockEventService.recordOnly(menuId, orderId, orderLineId, "stock.reserved", qty, null);
                // Outbox
                outbox.append(StockEvents.RESERVED, AGG_TYPE, menuId,
                        Map.of("menuId", menuId, "orderId", orderId, "orderLineId", orderLineId,
                                "qty", qty, "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                        Map.of("correlationId", orderId.toString()));
                return;
            } catch (Exception e) {
                log.warn("[HOT] Lua path failed ({}). Fallback Phase A", e.getMessage());
            }
        }

        // === 비핫키 경로 ===
        locks.withMenuLock(menuId.toString(), () -> {
            if (resRepo.findByOrderLineId(orderLineId).isPresent()) return null;

            int updated = stockRepo.reserve(menuId, qty);
            if (updated == 0) {
                stockEventService.recordOnly(menuId, orderId, orderLineId, "stock.insufficient", qty, "OUT_OF_STOCK");
                outbox.append(StockEvents.INSUFFICIENT, AGG_TYPE, menuId,
                        Map.of("menuId", menuId, "orderId", orderId, "orderLineId", orderLineId,
                                "requestedQty", qty, "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                        Map.of("correlationId", orderId.toString(), "reason", "OUT_OF_STOCK"));
                return null;
            }

            resRepo.save(InventoryReservation.builder()
                    .reservationId(UUID.randomUUID())
                    .menuId(menuId)
                    .orderId(orderId)
                    .orderLineId(orderLineId)
                    .qty(qty)
                    .status("PENDING")
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .createdAt(LocalDateTime.now())
                    .build());

            stockEventService.recordOnly(menuId, orderId, orderLineId, "stock.reserved", qty, null);
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

        locks.withMenuLock(r.getMenuId().toString(), () -> {
            int u = stockRepo.consume(r.getMenuId(), r.getQty());
            if (u == 0) throw new IllegalStateException("RESERVED_UNDERFLOW");

            r.setStatus("CONFIRMED");
            resRepo.save(r);

            stockEventService.recordOnly(r.getMenuId(), r.getOrderId(), r.getOrderLineId(), "stock.committed", r.getQty(), null);
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

        locks.withMenuLock(r.getMenuId().toString(), () -> {
            r.setStatus("CANCELED");
            r.setReason(reason);
            resRepo.save(r);

            stockRepo.release(r.getMenuId(), r.getQty());

            stockEventService.recordOnly(r.getMenuId(), r.getOrderId(), r.getOrderLineId(), "stock.released", r.getQty(), reason);
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
        locks.withMenuLock(menuId.toString(), () -> {
            int u = stockRepo.adjust(menuId, delta);
            if (u == 0) throw new IllegalStateException("ADJUST_FAILED");

            stockEventService.recordOnly(menuId,
                    UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    "stock.adjusted", Math.abs(delta), delta >= 0 ? "ADMIN_INCREASE" : "ADMIN_DECREASE");

            outbox.append(StockEvents.ADJUSTED, AGG_TYPE, menuId,
                    Map.of("menuId", menuId, "delta", delta, "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                    Map.of("correlationId", "ADMIN-ADJUST"));
            return null;
        });
    }

    @Override
    @Transactional
    public void cancelAfterConfirm(UUID orderLineId, String reason) {
        var r = resRepo.findByOrderLineId(orderLineId)
                .orElseThrow(() -> new IllegalArgumentException("NO_RESERVATION"));
        if ("REFUNDED".equals(r.getStatus()) || "CANCELED_AFTER_CONFIRM".equals(r.getStatus())) return;
        if (!"CONFIRMED".equals(r.getStatus())) { cancel(orderLineId, reason); return; }

        locks.withMenuLock(r.getMenuId().toString(), () -> {
            int u = stockRepo.adjust(r.getMenuId(), +r.getQty());
            if (u == 0) throw new IllegalStateException("ADJUST_FAILED_AFTER_CONFIRM");

            r.setStatus("REFUNDED");
            r.setReason(reason);
            resRepo.save(r);

            stockEventService.recordOnly(r.getMenuId(), r.getOrderId(), r.getOrderLineId(), "stock.returned", r.getQty(), reason);
            outbox.append("stock.returned", AGG_TYPE, r.getMenuId(),
                    Map.of("menuId", r.getMenuId(), "orderId", r.getOrderId(), "orderLineId", r.getOrderLineId(),
                            "qty", r.getQty(), "reason", reason, "occurredAt", LocalDateTime.now(), "eventVersion", 1),
                    Map.of("correlationId", r.getOrderId().toString()));
            return null;
        });
    }

    public static class InsufficientStockException extends RuntimeException {}
    public static class AlreadyProcessedException extends RuntimeException {}
}
