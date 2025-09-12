package com.eatcloud.paymentservice.kafka.consumer;

import com.eatcloud.paymentservice.event.PaymentRequestCancelEvent;
import com.eatcloud.paymentservice.event.PaymentRequestCancelResponseEvent;
import com.eatcloud.paymentservice.service.PaymentService;
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
            log.info("결제 요청 취소 처리 시작: orderId={}", event.getOrderId());
            paymentService.cancelPaymentByOrder(event.getOrderId(), "Saga 보상 트랜잭션");
            log.info("결제 요청 취소 처리 완료: orderId={}", event.getOrderId());

            PaymentRequestCancelResponseEvent responseEvent = PaymentRequestCancelResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .sagaId(event.getSagaId())
                    .success(true)
                    .errorMessage(null)
                    .build();
            
            kafkaTemplate.send("payment.request.cancel.response", event.getOrderId().toString(), responseEvent);
            log.info("결제 요청 취소 성공 응답 발행: orderId={}, sagaId={}", event.getOrderId(), event.getSagaId());
            
        } catch (Exception e) {
            log.error("결제 요청 취소 처리 중 오류 발생: orderId={}, sagaId={}", 
                    event.getOrderId(), event.getSagaId(), e);

            PaymentRequestCancelResponseEvent responseEvent = PaymentRequestCancelResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .sagaId(event.getSagaId())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
            
            kafkaTemplate.send("payment.request.cancel.response", event.getOrderId().toString(), responseEvent);
            log.info("결제 요청 취소 실패 응답 발행: orderId={}, sagaId={}", event.getOrderId(), event.getSagaId());
        }
    }
}
