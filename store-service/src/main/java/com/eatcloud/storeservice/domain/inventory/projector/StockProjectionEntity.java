// package: com.eatcloud.storeservice.domain.inventory.projector
package com.eatcloud.storeservice.domain.inventory.projector;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_projection")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockProjectionEntity {

    @Id
    private UUID menuId;

    @Column(nullable = false)
    private Integer avail;   // 현재 가용(= 입고 - 예약 + 반납)

    @Column(nullable = false)
    private Integer reserved; // 예약 중(아직 commit 전)

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (avail == null) avail = 0;
        if (reserved == null) reserved = 0;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
