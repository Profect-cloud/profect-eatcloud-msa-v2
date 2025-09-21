package com.eatcloud.orderservice.read;

import jakarta.persistence.*; import lombok.*;
import java.time.LocalDateTime; import java.util.UUID;

@Entity @Table(name="processed_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessedEvent {
    @Id @Column(name="event_id")
    private UUID eventId;

    @Column(name="processed_at", nullable=false)
    private LocalDateTime processedAt;
}
