package com.eatcloud.orderservice.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxMappingValidator {

    private final OutboxMappingProperties mappingProperties;

    private static final List<String> REQUIRED_EVENT_TYPES = List.of(
            "OrderCreatedEvent",
            "OrderCancelledEvent",
            "PointDeductionRequestEvent"
    );

    @PostConstruct
    public void validate() {
        for (String eventType : REQUIRED_EVENT_TYPES) {
            String topic = mappingProperties.resolveTopic(eventType);
            if (topic == null || topic.isBlank()) {
                throw new IllegalStateException("Outbox 토픽 매핑 누락: eventType=" + eventType +
                        ". application.properties의 outbox.mapping." + eventType + "=... 값을 설정하세요.");
            }
        }
    }
}


