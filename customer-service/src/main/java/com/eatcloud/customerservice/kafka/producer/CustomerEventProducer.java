package com.eatcloud.customerservice.kafka.producer;

import com.eatcloud.customerservice.event.PointReservationResponseEvent;
import com.eatcloud.customerservice.event.PointReservationCancelResponseEvent;
import com.eatcloud.customerservice.event.PointDeductionResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerEventProducer {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public void publishPointReservationResponse(PointReservationResponseEvent event) {
        try {
            kafkaTemplate.send("point.reservation.response", event.getOrderId().toString(), event);
            log.info("포인트 예약 응답 이벤트 발행: orderId={}, success={}", 
                    event.getOrderId(), event.isSuccess());
        } catch (Exception e) {
            log.error("포인트 예약 응답 이벤트 발행 실패: orderId={}", event.getOrderId(), e);
            throw new RuntimeException("포인트 예약 응답 이벤트 발행 실패: " + e.getMessage(), e);
        }
    }
    
    public void publishPointReservationCancelResponse(PointReservationCancelResponseEvent event) {
        try {
            kafkaTemplate.send("point.reservation.cancel.response", event.getOrderId().toString(), event);
            log.info("포인트 예약 취소 응답 이벤트 발행: orderId={}, success={}", 
                    event.getOrderId(), event.isSuccess());
        } catch (Exception e) {
            log.error("포인트 예약 취소 응답 이벤트 발행 실패: orderId={}", event.getOrderId(), e);
            throw new RuntimeException("포인트 예약 취소 응답 이벤트 발행 실패: " + e.getMessage(), e);
        }
    }
    
    public void publishPointDeductionResponse(PointDeductionResponseEvent event) {
        try {
            kafkaTemplate.send("point.deduction.response", event.getOrderId().toString(), event);
            log.info("포인트 차감 응답 이벤트 발행: orderId={}, success={}", 
                    event.getOrderId(), event.isSuccess());
        } catch (Exception e) {
            log.error("포인트 차감 응답 이벤트 발행 실패: orderId={}", event.getOrderId(), e);
            throw new RuntimeException("포인트 차감 응답 이벤트 발행 실패: " + e.getMessage(), e);
        }
    }
}
