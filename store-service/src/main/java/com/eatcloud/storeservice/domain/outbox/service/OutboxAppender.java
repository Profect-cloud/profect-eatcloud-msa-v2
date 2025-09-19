package com.eatcloud.storeservice.domain.outbox.service;

import java.util.Map;
import java.util.UUID;

public interface OutboxAppender {
    void append(String eventType, String aggregateType, UUID aggregateId,
                Map<String, ?> payloadMap, Map<String, ?> headersMap);
    void append(String eventType, String aggregateType, UUID aggregateId,
                Map<String, ?> payloadMap);
}