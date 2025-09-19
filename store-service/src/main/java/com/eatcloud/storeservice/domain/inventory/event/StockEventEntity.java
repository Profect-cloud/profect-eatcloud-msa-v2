package com.eatcloud.storeservice.domain.inventory.event;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_events")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockEventEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID menuId;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID orderLineId;

    @Column(nullable = false)
    private String eventType;  // reserved, committed, released, insufficient 등

    @Column(nullable = false)
    private Integer quantity;

    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
