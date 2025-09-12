package com.eatcloud.customerservice.kafka.consumer;

import com.eatcloud.customerservice.entity.Customer;
import com.eatcloud.customerservice.event.PointReservationCancelEvent;
import com.eatcloud.customerservice.event.PointReservationCancelResponseEvent;
import com.eatcloud.customerservice.kafka.producer.CustomerEventProducer;
import com.eatcloud.customerservice.service.CustomerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointReservationCancelEventConsumer {

    private final CustomerService customerService;
    private final CustomerEventProducer customerEventProducer;

    @KafkaListener(topics = "point.reservation.cancel", groupId = "customer-service", containerFactory = "kafkaListenerContainerFactory")
    public void handlePointReservationCancel(PointReservationCancelEvent event) {
        log.info("PointReservationCancelEvent 수신: orderId={}, customerId={}, sagaId={}", 
                event.getOrderId(), event.getCustomerId(), event.getSagaId());
        
        try {
            // TODO: 향후 PointReservation 엔티티 추가 시 orderId 기반 취소로 개선
            Customer customer = customerService.getCustomer(event.getCustomerId());
            if (customer != null && customer.getReservedPoints() > 0) {
                customerService.cancelReservedPoints(event.getCustomerId(), customer.getReservedPoints());
            }
            
            PointReservationCancelResponseEvent responseEvent = PointReservationCancelResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .sagaId(event.getSagaId())
                    .success(true)
                    .errorMessage(null)
                    .build();
            
            customerEventProducer.publishPointReservationCancelResponse(responseEvent);
            log.info("포인트 예약 취소 성공 응답 발행: orderId={}, sagaId={}", event.getOrderId(), event.getSagaId());
            
        } catch (Exception e) {
            log.error("포인트 예약 취소 처리 중 오류 발생: orderId={}, customerId={}, sagaId={}", 
                    event.getOrderId(), event.getCustomerId(), event.getSagaId(), e);

            PointReservationCancelResponseEvent responseEvent = PointReservationCancelResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .sagaId(event.getSagaId())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
            
            customerEventProducer.publishPointReservationCancelResponse(responseEvent);
            log.info("포인트 예약 취소 실패 응답 발행: orderId={}, sagaId={}", event.getOrderId(), event.getSagaId());
        }
    }
}
