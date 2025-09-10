package com.eatcloud.paymentservice.service;

import com.eatcloud.paymentservice.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentCompleted(UUID orderId, UUID customerId, UUID paymentId, 
                                      Integer totalAmount, Integer amount, Integer pointsUsed, 
                                      String paymentMethod, String transactionId) {
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .orderId(orderId)
                .customerId(customerId)
                .paymentId(paymentId)
                .totalAmount(totalAmount)
                .amount(amount)
                .pointsUsed(pointsUsed)
                .paymentMethod(paymentMethod)
                .transactionId(transactionId)
                .completedAt(LocalDateTime.now())
                .build();

        try {
            kafkaTemplate.send("payment.completed", event);
            log.info("PaymentCompletedEvent 발행 완료: orderId={}, paymentId={}", orderId, paymentId);
        } catch (Exception e) {
            log.error("PaymentCompletedEvent 발행 실패: orderId={}, paymentId={}", orderId, paymentId, e);
            throw new RuntimeException("결제 완료 이벤트 발행에 실패했습니다: " + e.getMessage(), e);
        }
    }
}
