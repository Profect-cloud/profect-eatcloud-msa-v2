package com.eatcloud.customerservice.service;

import com.eatcloud.customerservice.event.PointReservationRequestEvent;
import com.eatcloud.customerservice.event.PointReservationResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointReservationEventConsumer {

    private final CustomerService customerService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "point.reservation.request", groupId = "customer-service", containerFactory = "kafkaListenerContainerFactory")
    public void handlePointReservationRequest(PointReservationRequestEvent event) {
        log.info("PointReservationRequestEvent 수신: orderId={}, customerId={}, points={}, sagaId={}", 
                event.getOrderId(), event.getCustomerId(), event.getPoints(), event.getSagaId());
        
        try {
            // 포인트 예약 처리
            customerService.reservePoints(event.getCustomerId(), event.getPoints());
            
            // 성공 응답 이벤트 발행
            PointReservationResponseEvent responseEvent = PointReservationResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .sagaId(event.getSagaId())
                    .reservationId(UUID.randomUUID().toString())
                    .success(true)
                    .errorMessage(null)
                    .build();
            
            kafkaTemplate.send("point.reservation.response", responseEvent);
            log.info("포인트 예약 성공 응답 발행: orderId={}, sagaId={}", event.getOrderId(), event.getSagaId());
            
        } catch (Exception e) {
            log.error("포인트 예약 처리 중 오류 발생: orderId={}, customerId={}, sagaId={}", 
                    event.getOrderId(), event.getCustomerId(), event.getSagaId(), e);
            
            // 실패 응답 이벤트 발행
            PointReservationResponseEvent responseEvent = PointReservationResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .sagaId(event.getSagaId())
                    .reservationId(null)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
            
            kafkaTemplate.send("point.reservation.response", responseEvent);
            log.info("포인트 예약 실패 응답 발행: orderId={}, sagaId={}", event.getOrderId(), event.getSagaId());
        }
    }
}
