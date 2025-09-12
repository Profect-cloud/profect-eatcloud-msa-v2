package com.eatcloud.orderservice.kafka.consumer;

import com.eatcloud.orderservice.event.PointDeductionResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointDeductionResponseEventConsumer {

    @KafkaListener(topics = "point.deduction.response", groupId = "order-service-deduction", 
                   containerFactory = "pointDeductionKafkaListenerContainerFactory")
    public void handlePointDeductionResponse(PointDeductionResponseEvent event) {
        log.info("PointDeductionResponseEvent 수신: orderId={}, customerId={}, pointsUsed={}, success={}", 
                event.getOrderId(), event.getCustomerId(), event.getPointsUsed(), event.isSuccess());

        try {
            if (event.isSuccess()) {
                log.info("포인트 차감 성공: orderId={}, customerId={}, pointsUsed={}", 
                        event.getOrderId(), event.getCustomerId(), event.getPointsUsed());
            } else {
                log.error("포인트 차감 실패: orderId={}, customerId={}, pointsUsed={}, error={}", 
                        event.getOrderId(), event.getCustomerId(), event.getPointsUsed(), event.getMessage());
            }
            
        } catch (Exception e) {
            log.error("포인트 차감 응답 처리 중 오류 발생: orderId={}, customerId={}", 
                    event.getOrderId(), event.getCustomerId(), e);
            throw e;
        }
    }
}
