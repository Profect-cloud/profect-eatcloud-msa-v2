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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

    private static final String EVT_RESERVED = "stock.reserved";
    private static final String EVT_RELEASED = "stock.released";
    private static final String EVT_ADJUSTED = "stock.adjusted";

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

        locks.withMenuLock(r.getMenuId().toString(), () -> {
            int u = stockRepo.consume(r.getMenuId(), r.getQty());
            if (u == 0) throw new IllegalStateException("RESERVED_UNDERFLOW");

            r.setStatus("CONFIRMED");
            resRepo.save(r);

            outbox.append(StockEvents.CONFIRMED, r.getMenuId(), Map.of(
                    "menuId", r.getMenuId(),
                    "orderId", r.getOrderId(),
                    "orderLineId", r.getOrderLineId(),
                    "qty", r.getQty(),
                    "occurredAt", LocalDateTime.now(),
                    "eventVersion", 1
            ));
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

            outbox.append(StockEvents.RELEASED, r.getMenuId(), Map.of(
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

    @Override
    @Transactional
    public void adjust(UUID menuId, int delta) {
        locks.withMenuLock(menuId.toString(), () -> {
            int u = stockRepo.adjust(menuId, delta);
            if (u == 0) throw new IllegalStateException("ADJUST_FAILED");
            outbox.append(StockEvents.ADJUSTED, menuId, Map.of(
                    "menuId", menuId, "delta", delta, "occurredAt", LocalDateTime.now(), "eventVersion", 1
            ));
            return null;
        });
    }

    public static class InsufficientStockException extends RuntimeException {}
    public static class AlreadyProcessedException extends RuntimeException {}

}
