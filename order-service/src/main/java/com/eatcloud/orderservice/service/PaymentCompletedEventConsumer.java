package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCompletedEventConsumer {

    private final AsyncOrderCompletionService asyncOrderCompletionService;

    @KafkaListener(topics = "payment.completed", groupId = "order-service-completion")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("PaymentCompletedEvent 수신: orderId={}, customerId={}, paymentId={}", 
                event.getOrderId(), event.getCustomerId(), event.getPaymentId());
        
        try {
            asyncOrderCompletionService.processOrderCompletion(event);
        } catch (Exception e) {
            log.error("결제 완료 처리 중 오류 발생: orderId={}, paymentId={}", 
                    event.getOrderId(), event.getPaymentId(), e);
            throw e; // 재시도를 위해 예외를 다시 던짐
        }
    }
}