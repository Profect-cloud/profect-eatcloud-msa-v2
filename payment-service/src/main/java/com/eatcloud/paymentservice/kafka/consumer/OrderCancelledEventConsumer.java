package com.eatcloud.paymentservice.kafka.consumer;

import com.eatcloud.logging.annotation.Loggable;
import com.eatcloud.paymentservice.event.OrderCancelledEvent;
import com.eatcloud.paymentservice.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public class OrderCancelledEventConsumer {

    private final PaymentService paymentService;

    @KafkaListener(topics = "order.cancelled", groupId = "payment-service")
    @Transactional
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("주문 취소 이벤트 수신: orderId={}, customerId={}, reason={}",
                event.getOrderId(), event.getCustomerId(), event.getCancelReason());

        try {
            paymentService.cancelPaymentByOrder(event.getOrderId(), event.getCancelReason());
            
            log.info("주문 취소로 인한 결제 취소 처리 완료: orderId={}, customerId={}", 
                    event.getOrderId(), event.getCustomerId());

        } catch (Exception e) {
            log.error("주문 취소 이벤트 처리 실패: orderId={}", event.getOrderId(), e);
            throw e;
        }
    }
}
