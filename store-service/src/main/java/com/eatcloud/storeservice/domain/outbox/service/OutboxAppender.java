package com.eatcloud.storeservice.domain.outbox.service;

import com.eatcloud.storeservice.domain.outbox.entity.Outbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public abstract class OutboxAppender {
    private final com.eatcloud.storeservice.domain.outbox.repository.OutboxRepository repo;
    private final ObjectMapper om;

    @Transactional
    public void append(String eventType, UUID aggregateId, Map<String, ?> payloadMap) {
        try {
            Outbox o = Outbox.builder()
                    .id(UUID.randomUUID())
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .payload(om.valueToTree(payloadMap))
                    .createdAt(LocalDateTime.now())
                    .build();
            repo.save(o);
        } catch (Exception e) {
            throw new RuntimeException("OUTBOX_APPEND_FAILED", e);
        }
    }
}
