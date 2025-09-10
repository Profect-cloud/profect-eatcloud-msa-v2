package com.eatcloud.paymentservice.service;

import com.eatcloud.paymentservice.event.PaymentRequestCancelEvent;
import com.eatcloud.paymentservice.event.PaymentRequestCancelResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRequestCancelEventConsumer {

    private final PaymentService paymentService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "payment.request.cancel", groupId = "payment-service", containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentRequestCancel(PaymentRequestCancelEvent event) {
        log.info("PaymentRequestCancelEvent 수신: orderId={}, sagaId={}", 
                event.getOrderId(), event.getSagaId());
        
        try {
            // 결제 요청 취소 처리
            // 실제로는 orderId로 결제 요청을 찾아서 취소해야 함
            // 현재는 단순히 로그만 출력
            log.info("결제 요청 취소 처리: orderId={}", event.getOrderId());
            
            // 성공 응답 이벤트 발행
            PaymentRequestCancelResponseEvent responseEvent = PaymentRequestCancelResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .sagaId(event.getSagaId())
                    .success(true)
                    .errorMessage(null)
                    .build();
            
            kafkaTemplate.send("payment.request.cancel.response", responseEvent);
            log.info("결제 요청 취소 성공 응답 발행: orderId={}, sagaId={}", event.getOrderId(), event.getSagaId());
            
        } catch (Exception e) {
            log.error("결제 요청 취소 처리 중 오류 발생: orderId={}, sagaId={}", 
                    event.getOrderId(), event.getSagaId(), e);
            
            // 실패 응답 이벤트 발행
            PaymentRequestCancelResponseEvent responseEvent = PaymentRequestCancelResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .sagaId(event.getSagaId())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
            
            kafkaTemplate.send("payment.request.cancel.response", responseEvent);
            log.info("결제 요청 취소 실패 응답 발행: orderId={}, sagaId={}", event.getOrderId(), event.getSagaId());
        }
    }
}
