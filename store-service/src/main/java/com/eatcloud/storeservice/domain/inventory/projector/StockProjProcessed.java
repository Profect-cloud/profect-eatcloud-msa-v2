// package: com.eatcloud.storeservice.domain.inventory.projector
package com.eatcloud.storeservice.domain.inventory.projector;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_proj_processed")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockProjProcessed {
    @Id
    private UUID eventId;
    private LocalDateTime processedAt;
}
