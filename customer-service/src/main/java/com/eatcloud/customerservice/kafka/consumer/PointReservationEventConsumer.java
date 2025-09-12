package com.eatcloud.customerservice.kafka.consumer;

import com.eatcloud.customerservice.event.PointReservationRequestEvent;
import com.eatcloud.customerservice.event.PointReservationResponseEvent;
import com.eatcloud.customerservice.kafka.producer.CustomerEventProducer;
import com.eatcloud.customerservice.service.CustomerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointReservationEventConsumer {

    private final CustomerService customerService;
    private final CustomerEventProducer customerEventProducer;

    @KafkaListener(topics = "point.reservation.request", groupId = "customer-service", containerFactory = "kafkaListenerContainerFactory")
    public void handlePointReservationRequest(PointReservationRequestEvent event) {
        log.info("PointReservationRequestEvent 수신: orderId={}, customerId={}, points={}, sagaId={}", 
                event.getOrderId(), event.getCustomerId(), event.getPoints(), event.getSagaId());

        try {
            customerService.reservePoints(event.getCustomerId(), event.getPoints());

            PointReservationResponseEvent responseEvent = PointReservationResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .sagaId(event.getSagaId())
                    .reservationId(UUID.randomUUID().toString())
                    .success(true)
                    .errorMessage(null)
                    .build();

            customerEventProducer.publishPointReservationResponse(responseEvent);

        } catch (Exception e) {
            log.error("포인트 예약 처리 중 오류 발생: orderId={}, customerId={}, sagaId={}",
                    event.getOrderId(), event.getCustomerId(), event.getSagaId(), e);

            PointReservationResponseEvent responseEvent = PointReservationResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .sagaId(event.getSagaId())
                    .reservationId(null)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();

            customerEventProducer.publishPointReservationResponse(responseEvent);
        }
    }
}
