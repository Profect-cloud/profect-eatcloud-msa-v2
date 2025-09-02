package com.eatcloud.customerservice.service;

import com.eatcloud.customerservice.event.PaymentCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PointReservationService pointReservationService;
    private final ObjectMapper objectMapper;
    private final com.eatcloud.customerservice.circuit.CircuitBreaker circuitBreaker;

    @KafkaListener(topics = "payment.created", groupId = "customer-service", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handlePaymentCreated(String eventJson) {
        if (!circuitBreaker.canExecute()) {
            log.warn("Circuit Breaker가 OPEN 상태입니다. 메시지 처리를 건너뜁니다: {}", eventJson);
            return;
        }

        try {
            log.info("결제 생성 이벤트 수신 (JSON): {}", eventJson);

            PaymentCreatedEvent event = objectMapper.readValue(eventJson, PaymentCreatedEvent.class);
            
            log.info("결제 생성 이벤트 파싱 완료: paymentId={}, orderId={}, customerId={}, status={}",
                    event.getPaymentId(), event.getOrderId(), event.getCustomerId(), event.getPaymentStatus());

            if ("COMPLETED".equals(event.getPaymentStatus())) {
                pointReservationService.processReservation(event.getOrderId());
                log.info("결제 완료로 인한 포인트 예약 처리 완료: orderId={}, paymentId={}", 
                        event.getOrderId(), event.getPaymentId());
            } else if ("FAILED".equals(event.getPaymentStatus()) || "CANCELLED".equals(event.getPaymentStatus())) {
                pointReservationService.cancelReservation(event.getOrderId());
                log.info("결제 실패/취소로 인한 포인트 예약 취소 완료: orderId={}, paymentId={}, status={}", 
                        event.getOrderId(), event.getPaymentId(), event.getPaymentStatus());
            } else {
                log.info("결제가 성공하지 않음: orderId={}, status={}", event.getOrderId(), event.getPaymentStatus());
            }

            circuitBreaker.onSuccess();

        } catch (JsonProcessingException e) {
            log.error("결제 생성 이벤트 JSON 파싱 실패: eventJson={}", eventJson, e);
            circuitBreaker.onFailure(e);
        } catch (Exception e) {
            log.error("결제 생성 이벤트 처리 실패: eventJson={}", eventJson, e);
            circuitBreaker.onFailure(e);
            throw e;
        }
    }
}
