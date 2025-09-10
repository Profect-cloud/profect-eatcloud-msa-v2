package com.eatcloud.paymentservice.service;

import com.eatcloud.paymentservice.event.PaymentRequestEvent;
import com.eatcloud.paymentservice.event.PaymentRequestResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRequestEventConsumer {

    private final PaymentService paymentService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "payment.request", groupId = "payment-service", containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentRequest(PaymentRequestEvent event) {
        log.info("PaymentRequestEvent 수신: orderId={}, customerId={}, amount={}, sagaId={}", 
                event.getOrderId(), event.getCustomerId(), event.getAmount(), event.getSagaId());
        
        try {
            // 결제 요청 생성
            var paymentRequest = paymentService.createPaymentRequest(
                event.getOrderId(), 
                event.getCustomerId(), 
                event.getAmount()
            );
            
            // 성공 응답 이벤트 발행
            PaymentRequestResponseEvent responseEvent = PaymentRequestResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .sagaId(event.getSagaId())
                    .paymentUrl(paymentRequest.getRedirectUrl())
                    .success(true)
                    .errorMessage(null)
                    .build();
            
            kafkaTemplate.send("payment.request.response", responseEvent);
            log.info("결제 요청 성공 응답 발행: orderId={}, sagaId={}, paymentUrl={}", 
                    event.getOrderId(), event.getSagaId(), paymentRequest.getRedirectUrl());
            
        } catch (Exception e) {
            log.error("결제 요청 처리 중 오류 발생: orderId={}, customerId={}, sagaId={}", 
                    event.getOrderId(), event.getCustomerId(), event.getSagaId(), e);
            
            // 실패 응답 이벤트 발행
            PaymentRequestResponseEvent responseEvent = PaymentRequestResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .sagaId(event.getSagaId())
                    .paymentUrl(null)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
            
            kafkaTemplate.send("payment.request.response", responseEvent);
            log.info("결제 요청 실패 응답 발행: orderId={}, sagaId={}", event.getOrderId(), event.getSagaId());
        }
    }
}
