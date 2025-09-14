package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.entity.OutboxEvent;
import com.eatcloud.orderservice.config.OutboxMappingProperties;
import com.eatcloud.orderservice.repository.OutboxEventRepository;
import com.eatcloud.orderservice.event.OrderCreatedEvent;
import com.eatcloud.orderservice.event.OrderCancelledEvent;
import com.eatcloud.orderservice.event.PointDeductionRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxMappingProperties mappingProperties;
    private final ObjectMapper objectMapper;

    @Value("${outbox.publisher.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> candidates = outboxEventRepository
                .findByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                        Arrays.asList(OutboxEvent.Status.PENDING, OutboxEvent.Status.FAILED),
                        LocalDateTime.now(),
                        PageRequest.of(0, batchSize)
                );

        for (OutboxEvent event : candidates) {
            try {
                String topic = resolveTopic(event.getEventType());
                Object value = deserializePayload(event.getEventType(), event.getPayload());
                kafkaTemplate.send(new ProducerRecord<>(topic, event.getAggregateId(), value));

                eventSuccess(event);
                log.debug("Outbox 이벤트 발행 성공: eventId={}, type={}, topic={}", event.getEventId(), event.getEventType(), topic);
            } catch (Exception e) {
                eventFailure(event, e);
                log.error("Outbox 이벤트 발행 실패: eventId={}, type={}, error={}", event.getEventId(), event.getEventType(), e.getMessage());
            }
        }
    }

    private String resolveTopic(String eventType) {
        String topic = mappingProperties.resolveTopic(eventType);
        if (topic == null || topic.isBlank()) {
            throw new IllegalStateException("Outbox 토픽 매핑 누락: eventType=" + eventType +
                    ". application.properties의 outbox.mapping." + eventType + "=... 값을 설정하세요.");
        }
        return topic;
    }

    private Object deserializePayload(String eventType, String payloadJson) throws Exception {
        return switch (eventType) {
            case "OrderCreatedEvent" -> objectMapper.readValue(payloadJson, OrderCreatedEvent.class);
            case "OrderCancelledEvent" -> objectMapper.readValue(payloadJson, OrderCancelledEvent.class);
            case "PointDeductionRequestEvent" -> objectMapper.readValue(payloadJson, PointDeductionRequestEvent.class);
            default -> throw new IllegalArgumentException("역직렬화 클래스를 찾을 수 없습니다: " + eventType);
        };
    }

    private void eventSuccess(OutboxEvent event) {
        eventSuccessOrFailure(event, true, null);
    }

    private void eventFailure(OutboxEvent event, Exception e) {
        eventSuccessOrFailure(event, false, e);
    }

    private void eventSuccessOrFailure(OutboxEvent event, boolean success, Exception e) {
        if (success) {
            eventSuccessUpdate(event);
        } else {
            eventFailureUpdate(event);
        }
    }

    private void eventSuccessUpdate(OutboxEvent event) {
        event.setStatus(OutboxEvent.Status.SENT);
        event.setNextAttemptAt(null);
        outboxEventRepository.save(event);
    }

    private void eventFailureUpdate(OutboxEvent event) {
        int retry = event.getRetryCount() + 1;
        event.setRetryCount(retry);
        event.setStatus(OutboxEvent.Status.FAILED);
        long backoffSec = Math.min(60, (long) Math.pow(2, Math.min(5, retry)));
        event.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoffSec));
        outboxEventRepository.save(event);
    }
}


