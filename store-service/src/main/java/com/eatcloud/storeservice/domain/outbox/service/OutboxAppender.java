package com.eatcloud.storeservice.domain.outbox.service;

import java.util.Map;
import java.util.UUID;

public interface OutboxAppender {
    void append(String eventType, UUID aggregateId, Map<String, ?> payloadMap);
}
