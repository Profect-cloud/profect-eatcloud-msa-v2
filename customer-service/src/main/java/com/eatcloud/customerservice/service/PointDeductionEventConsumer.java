package com.eatcloud.customerservice.service;

import com.eatcloud.customerservice.event.PointDeductionRequestEvent;
import com.eatcloud.customerservice.event.PointDeductionResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointDeductionEventConsumer {

    private final CustomerService customerService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "point.deduction.request", groupId = "customer-service-deduction", 
                   containerFactory = "pointDeductionKafkaListenerContainerFactory")
    public void handlePointDeductionRequest(PointDeductionRequestEvent event) {
        log.info("PointDeductionRequestEvent 수신: orderId={}, customerId={}, pointsUsed={}", 
                event.getOrderId(), event.getCustomerId(), event.getPointsUsed());

        try {
            // 예약된 포인트를 실제로 차감
            customerService.processReservedPoints(event.getCustomerId(), event.getPointsUsed());

            // 성공 응답 이벤트 발행
            PointDeductionResponseEvent responseEvent = PointDeductionResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .pointsUsed(event.getPointsUsed())
                    .sagaId(event.getSagaId())
                    .success(true)
                    .message("포인트 차감 완료")
                    .build();

            kafkaTemplate.send("point.deduction.response", responseEvent);
            log.info("포인트 차감 완료: orderId={}, customerId={}, pointsUsed={}", 
                    event.getOrderId(), event.getCustomerId(), event.getPointsUsed());

        } catch (Exception e) {
            log.error("포인트 차감 처리 실패: orderId={}, customerId={}, pointsUsed={}", 
                     event.getOrderId(), event.getCustomerId(), event.getPointsUsed(), e);

            // 실패 응답 이벤트 발행
            PointDeductionResponseEvent responseEvent = PointDeductionResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .pointsUsed(event.getPointsUsed())
                    .sagaId(event.getSagaId())
                    .success(false)
                    .message("포인트 차감 실패: " + e.getMessage())
                    .build();

            kafkaTemplate.send("point.deduction.response", responseEvent);
        }
    }
}
