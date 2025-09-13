package com.eatcloud.customerservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "processed_events", indexes = {
        @Index(name = "idx_processed_event_unique", columnList = "event_type,order_id", unique = true)
})
public class ProcessedEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 150)
    private String eventType;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private ProcessedEvent(String eventType, UUID orderId, LocalDateTime createdAt) {
        this.eventType = eventType;
        this.orderId = orderId;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }
}


