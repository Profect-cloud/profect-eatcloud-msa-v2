package com.eatcloud.storeservice.domain.outbox.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_outbox")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Outbox {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Type(JsonBinaryType.class)                   // 🔴 jsonb 매핑
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;                     // 🔴 String → JsonNode 로 변경

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private boolean sent;
}
