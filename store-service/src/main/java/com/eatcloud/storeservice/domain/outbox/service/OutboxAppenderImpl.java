package com.eatcloud.storeservice.domain.outbox.service;

import com.eatcloud.storeservice.domain.outbox.entity.Outbox;
import com.eatcloud.storeservice.domain.outbox.repository.OutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxAppenderImpl implements OutboxAppender {

    private final OutboxRepository repo;
    private final ObjectMapper om;

    @Override
    @Transactional
    public void append(String eventType, String aggregateType, UUID aggregateId,
                       Map<String, ?> payloadMap, Map<String, ?> headersMap) {
        JsonNode payload = om.valueToTree(payloadMap);
        JsonNode headers = headersMap != null ? om.valueToTree(headersMap) : null;

        Outbox o = Outbox.builder()
                .id(UUID.randomUUID())
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .payload(payload)
                .headers(headers)
                .createdAt(LocalDateTime.now())
                .status(Outbox.Status.PENDING)
                .retryCount(0)
                .sent(false)
                .build();

        repo.save(o);
    }

    @Override
    @Transactional
    public void append(String eventType, String aggregateType, UUID aggregateId,
                       Map<String, ?> payloadMap) {
        append(eventType, aggregateType, aggregateId, payloadMap, null);
    }
}
