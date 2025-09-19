package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.entity.Order;
import com.eatcloud.orderservice.exception.OrderException;
import com.eatcloud.orderservice.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCompensationService {

    private final OrderService orderService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxService outboxService;

    /**
     * 재고 부족 비동기 보상 처리:
     * - 주문 상태를 CANCELLED로 전이(멱등)
     * - 포인트 예약 취소 이벤트 발행(있다면)
     * - 결제 요청 취소 이벤트 발행(있다면)
     * - OrderCancelledEvent Outbox 기록(멱등)
     */
    @Transactional
    public void compensateForStockShortage(UUID orderId, String reason) {
        Order order = orderService.findById(orderId)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND, "order not found"));

        // 이미 취소/완료 등 최종상태면 멱등 처리
        if (orderService.isFinalized(order)) {
            log.info("skip compensation; order already finalized. orderId={}", orderId);
            return;
        }

        // 1) 주문 취소 (사유 남기기)
        orderService.cancelOrder(orderId, reason); // 내부에서 멱등하게 처리하도록

        // 2) 포인트 예약 취소(사용한 경우만)
        if (Boolean.TRUE.equals(order.getUsePoints()) && order.getPointsToUse() != null && order.getPointsToUse() > 0) {
            var cancel = com.eatcloud.orderservice.event.PointReservationCancelEvent.builder()
                    .orderId(order.getOrderId())
                    .customerId(order.getCustomerId())
                    .sagaId(UUID.randomUUID().toString()) // 비동기니까 새 sagaId
                    .build();
            kafkaTemplate.send("point.reservation.cancel", order.getOrderId().toString(), cancel);
            log.info("emit point.reservation.cancel for orderId={}", orderId);
        }

        // 3) 결제 요청 취소
        {
            var cancel = com.eatcloud.orderservice.event.PaymentRequestCancelEvent.builder()
                    .orderId(order.getOrderId())
                    .sagaId(UUID.randomUUID().toString())
                    .build();
            kafkaTemplate.send("payment.request.cancel", order.getOrderId().toString(), cancel);
            log.info("emit payment.request.cancel for orderId={}", orderId);
        }

        // 4) Outbox - OrderCancelledEvent 기록(소비자에게 알림)
        try {
            var cancelledEvent = com.eatcloud.orderservice.event.OrderCancelledEvent.builder()
                    .orderId(order.getOrderId())
                    .customerId(order.getCustomerId())
                    .storeId(order.getStoreId())
                    .cancelReason(reason != null ? reason : "STOCK_INSUFFICIENT")
                    .build();

            outboxService.saveEvent(
                    "Order",
                    order.getOrderId().toString(),
                    "OrderCancelledEvent",
                    cancelledEvent,
                    outboxService.defaultHeaders(null, null)
            );
            log.info("Outbox OrderCancelledEvent saved. orderId={}", orderId);
        } catch (Exception e) {
            log.warn("Outbox OrderCancelledEvent save failed (will rely on retry): orderId={}, err={}", orderId, e.toString());
        }
    }
}
