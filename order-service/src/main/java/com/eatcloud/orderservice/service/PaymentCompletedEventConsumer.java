package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCompletedEventConsumer {

    private final AsyncOrderCompletionService asyncOrderCompletionService;

    /**
     * 결제 완료 이벤트 수신 → 비동기 후처리 시작
     * (주문 상태는 이미 PAID로 업데이트된 상태)
     */
    @KafkaListener(topics = "payment.completed", groupId = "order-service-completion")
    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("결제 완료 이벤트 수신: orderId={}, paymentId={}, customerId={}, pointsUsed={}", 
                event.getOrderId(), event.getPaymentId(), event.getCustomerId(), event.getPointsUsed());

        try {
            // 비동기 후처리 작업 시작 (포인트 차감)
            asyncOrderCompletionService.processOrderCompletion(event);
            
            log.info("결제 완료 이벤트 처리 완료: orderId={}, paymentId={}", 
                    event.getOrderId(), event.getPaymentId());

        } catch (Exception e) {
            log.error("결제 완료 이벤트 처리 실패: orderId={}, paymentId={}", 
                    event.getOrderId(), event.getPaymentId(), e);
            // DLQ는 KafkaConfig에서 자동 처리됨
            throw e;
        }
    }
}
