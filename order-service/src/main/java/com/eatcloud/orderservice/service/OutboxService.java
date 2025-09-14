package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.entity.OutboxEvent;
import com.eatcloud.orderservice.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveEvent(String aggregateType,
                          String aggregateId,
                          String eventType,
                          Object payload,
                          Map<String, Object> headers) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String headersJson = headers == null ? null : objectMapper.writeValueAsString(headers);

            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payloadJson)
                    .headers(headersJson)
                    .status(OutboxEvent.Status.PENDING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .nextAttemptAt(LocalDateTime.now())
                    .build();

            outboxEventRepository.save(event);
            log.info("Outbox 이벤트 저장: aggregateType={}, aggregateId={}, eventType={}", aggregateType, aggregateId, eventType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Outbox 이벤트 직렬화 실패: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> defaultHeaders(String traceId, String sagaId) {
        Map<String, Object> headers = new HashMap<>();
        if (traceId != null) headers.put("traceId", traceId);
        if (sagaId != null) headers.put("sagaId", sagaId);
        headers.put("sourceService", "order-service");
        return headers;
    }
}


