package com.eatcloud.customerservice.kafka.consumer;

import com.eatcloud.customerservice.event.OrderCancelledEvent;
import com.eatcloud.customerservice.service.PointReservationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCancelledEventConsumer {

    private final PointReservationService pointReservationService;

    @KafkaListener(topics = "order.cancelled", groupId = "customer-service")
    @Transactional
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("주문 취소 이벤트 수신: orderId={}, customerId={}, reason={}",
                event.getOrderId(), event.getCustomerId(), event.getCancelReason());

        try {
            pointReservationService.cancelReservation(event.getOrderId());
            
            log.info("주문 취소로 인한 포인트 예약 취소 완료: orderId={}, customerId={}", 
                    event.getOrderId(), event.getCustomerId());

        } catch (Exception e) {
            log.error("주문 취소 이벤트 처리 실패: orderId={}", event.getOrderId(), e);
            // TODO: Dead Letter Queue 구현 필요
            throw e;
        }
    }
}
