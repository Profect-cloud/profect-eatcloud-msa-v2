package com.eatcloud.customerservice.service;

import com.eatcloud.customerservice.event.PointReservationCancelEvent;
import com.eatcloud.customerservice.event.PointReservationCancelResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointReservationCancelEventConsumer {

    private final CustomerService customerService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "point.reservation.cancel", groupId = "customer-service", containerFactory = "kafkaListenerContainerFactory")
    public void handlePointReservationCancel(PointReservationCancelEvent event) {
        log.info("PointReservationCancelEvent 수신: orderId={}, customerId={}, sagaId={}", 
                event.getOrderId(), event.getCustomerId(), event.getSagaId());
        
        try {
            // 포인트 예약 취소 처리
            // 실제로는 orderId로 예약을 찾아서 취소해야 함
            // 현재는 단순히 고객의 예약 포인트를 취소
            customerService.cancelReservedPoints(event.getCustomerId(), 0);
            
            // 성공 응답 이벤트 발행
            PointReservationCancelResponseEvent responseEvent = PointReservationCancelResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .sagaId(event.getSagaId())
                    .success(true)
                    .errorMessage(null)
                    .build();
            
            kafkaTemplate.send("point.reservation.cancel.response", responseEvent);
            log.info("포인트 예약 취소 성공 응답 발행: orderId={}, sagaId={}", event.getOrderId(), event.getSagaId());
            
        } catch (Exception e) {
            log.error("포인트 예약 취소 처리 중 오류 발생: orderId={}, customerId={}, sagaId={}", 
                    event.getOrderId(), event.getCustomerId(), event.getSagaId(), e);
            
            // 실패 응답 이벤트 발행
            PointReservationCancelResponseEvent responseEvent = PointReservationCancelResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .sagaId(event.getSagaId())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
            
            kafkaTemplate.send("point.reservation.cancel.response", responseEvent);
            log.info("포인트 예약 취소 실패 응답 발행: orderId={}, sagaId={}", event.getOrderId(), event.getSagaId());
        }
    }
}
