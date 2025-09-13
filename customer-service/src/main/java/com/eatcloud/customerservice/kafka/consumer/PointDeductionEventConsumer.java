package com.eatcloud.customerservice.kafka.consumer;

import com.eatcloud.customerservice.event.PointDeductionRequestEvent;
import com.eatcloud.customerservice.event.PointDeductionResponseEvent;
import com.eatcloud.customerservice.kafka.producer.CustomerEventProducer;
import com.eatcloud.customerservice.entity.ProcessedEvent;
import com.eatcloud.customerservice.repository.ProcessedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import com.eatcloud.customerservice.service.CustomerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointDeductionEventConsumer {

    private final CustomerService customerService;
    private final CustomerEventProducer customerEventProducer;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "point.deduction.request", groupId = "customer-service-deduction", 
                   containerFactory = "pointDeductionKafkaListenerContainerFactory")
    public void handlePointDeductionRequest(PointDeductionRequestEvent event) {
        log.info("PointDeductionRequestEvent 수신: orderId={}, customerId={}, pointsUsed={}", 
                event.getOrderId(), event.getCustomerId(), event.getPointsUsed());

        try {
            // Idempotency guard: (eventType, orderId) unique
            String eventType = "PointDeductionRequestEvent";
            if (processedEventRepository.existsByEventTypeAndOrderId(eventType, event.getOrderId())) {
                log.info("중복 PointDeductionRequestEvent 무시: orderId={}", event.getOrderId());
                return;
            }
            try {
                processedEventRepository.save(ProcessedEvent.builder()
                        .eventType(eventType)
                        .orderId(event.getOrderId())
                        .build());
            } catch (DataIntegrityViolationException dup) {
                log.info("경합 중복 PointDeductionRequestEvent 무시: orderId={}", event.getOrderId());
                return;
            }
            customerService.processReservedPoints(event.getCustomerId(), event.getPointsUsed());

            PointDeductionResponseEvent responseEvent = PointDeductionResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .pointsUsed(event.getPointsUsed())
                    .sagaId(event.getSagaId())
                    .success(true)
                    .message("포인트 차감 완료")
                    .build();

            customerEventProducer.publishPointDeductionResponse(responseEvent);
            log.info("포인트 차감 완료: orderId={}, customerId={}, pointsUsed={}", 
                    event.getOrderId(), event.getCustomerId(), event.getPointsUsed());

        } catch (Exception e) {
            log.error("포인트 차감 처리 실패: orderId={}, customerId={}, pointsUsed={}", 
                     event.getOrderId(), event.getCustomerId(), event.getPointsUsed(), e);

            PointDeductionResponseEvent responseEvent = PointDeductionResponseEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .pointsUsed(event.getPointsUsed())
                    .sagaId(event.getSagaId())
                    .success(false)
                    .message("포인트 차감 실패: " + e.getMessage())
                    .build();

            customerEventProducer.publishPointDeductionResponse(responseEvent);
        }
    }
}
