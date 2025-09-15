package com.eatcloud.storeservice.domain.outbox.service;

import com.eatcloud.storeservice.domain.outbox.entity.Outbox;
import com.eatcloud.storeservice.domain.outbox.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
    public void append(String eventType, UUID aggregateId, Map<String, ?> payloadMap) {
        try {
            JsonNode json = om.valueToTree(payloadMap); // 🔴 JsonNode 로 변환
            Outbox o = Outbox.builder()
                    .id(UUID.randomUUID())
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .payload(json)                // 🔴 JsonNode 를 그대로 저장
                    .createdAt(LocalDateTime.now())
                    .build();
            repo.save(o);
        } catch (Exception e) {
            throw new RuntimeException("OUTBOX_APPEND_FAILED", e);
        }
    }
}
