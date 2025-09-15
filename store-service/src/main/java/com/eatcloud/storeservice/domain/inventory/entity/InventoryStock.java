// InventoryStock.java
package com.eatcloud.storeservice.domain.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_stock")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryStock {

    @Id
    @Column(name = "menu_id", nullable = false)
    private UUID menuId;

    @Builder.Default
    @Column(name = "is_unlimited", nullable = false)
    private boolean isUnlimited = false;

    @Builder.Default
    @Column(name = "available_qty", nullable = false)
    private int availableQty = 0;

    @Builder.Default
    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
