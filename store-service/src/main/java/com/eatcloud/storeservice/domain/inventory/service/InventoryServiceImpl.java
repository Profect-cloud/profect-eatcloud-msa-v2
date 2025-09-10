package com.eatcloud.storeservice.domain.inventory.service;

import com.eatcloud.storeservice.domain.inventory.entity.InventoryReservation;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryReservationRepository;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryStockRepository;
import com.eatcloud.storeservice.domain.outbox.service.OutboxAppender;
import com.eatcloud.storeservice.support.lock.RedisLockExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static com.eatcloud.storeservice.support.lock.RedisLockExecutor.LockTimeoutException;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryStockRepository stockRepo;
    private final InventoryReservationRepository resRepo;
    private final OutboxAppender outbox;
    private final RedisLockExecutor locks;

    private static final String EVT_RESERVED = "stock.reserved";
    private static final String EVT_RELEASED = "stock.released";
    private static final String EVT_ADJUSTED = "stock.adjusted";

    @Override
    @Transactional
    public void reserve(UUID orderId, UUID orderLineId, UUID menuId, int qty) {
        locks.withMenuLock(menuId.toString(), () -> {
            // 멱등: 이미 존재하면 no-op
            if (resRepo.findByOrderLineId(orderLineId).isPresent()) return null;

            int updated = stockRepo.reserve(menuId, qty); // CAS
            if (updated == 0) throw new InsufficientStockException();

            InventoryReservation r = InventoryReservation.builder()
                    .reservationId(UUID.randomUUID())
                    .menuId(menuId)
                    .orderId(orderId)
                    .orderLineId(orderLineId)
                    .qty(qty)
                    .status("PENDING")
                    .expiresAt(LocalDateTime.now().plus(10, ChronoUnit.MINUTES))
                    .createdAt(LocalDateTime.now())
                    .build();
            resRepo.save(r);

            // 로그(p_stock_logs)도 여기서 append (생략)

            outbox.append(EVT_RESERVED, menuId, Map.of(
                    "menuId", menuId, "orderId", orderId, "orderLineId", orderLineId,
                    "qty", qty, "occurredAt", LocalDateTime.now(), "eventVersion", 1
            ));
            return null;
        });
    }

    @Override
    @Transactional
    public void confirm(UUID orderLineId) {
        // 1) 예약 조회
        InventoryReservation r = resRepo.findByOrderLineId(orderLineId)
                .orElseThrow(() -> new IllegalArgumentException("NO_RESERVATION"));

        // 멱등: 이미 처리(확정/취소)면 아무 것도 하지 않음
        if (!"PENDING".equals(r.getStatus())) return;

        // 2) 동일 메뉴에 대해 락을 잡아 직렬화
        locks.withMenuLock(r.getMenuId().toString(), () -> {
            // 3) reserved_qty에서 차감 (available은 예약 때 이미 감소함)
            int u = stockRepo.consume(r.getMenuId(), r.getQty());
            if (u == 0) {
                // reserved 부족(수동 보정 등) 시 실패 처리
                throw new IllegalStateException("RESERVED_UNDERFLOW");
            }

            // 4) 예약 상태 갱신
            r.setStatus("CONFIRMED");
            resRepo.save(r);

            // 5) 아웃박스 발행
            outbox.append("stock.confirmed", r.getMenuId(), Map.of(
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
        if (!"PENDING".equals(r.getStatus())) return; // 멱등

        // (핫키면) 락으로 직렬화
        locks.withMenuLock(r.getMenuId().toString(), () -> {
            r.setStatus("CANCELED");
            r.setReason(reason);
            resRepo.save(r);

            stockRepo.release(r.getMenuId(), r.getQty()); // CAS 복구

            outbox.append(EVT_RELEASED, r.getMenuId(), Map.of(
                    "menuId", r.getMenuId(), "orderId", r.getOrderId(), "orderLineId", r.getOrderLineId(),
                    "qty", r.getQty(), "reason", reason, "occurredAt", LocalDateTime.now(), "eventVersion", 1
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
            outbox.append(EVT_ADJUSTED, menuId, Map.of(
                    "menuId", menuId, "delta", delta, "occurredAt", LocalDateTime.now(), "eventVersion", 1
            ));
            return null;
        });
    }

    public static class InsufficientStockException extends RuntimeException {}
    public static class AlreadyProcessedException extends RuntimeException {}
}
