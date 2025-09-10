// InventoryReservation.java
package com.eatcloud.storeservice.domain.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_reservations")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReservation {

    @Id
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "menu_id", nullable = false)
    private UUID menuId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_line_id", nullable = false, unique = true)
    private UUID orderLineId;

    @Column(name = "qty", nullable = false)
    private int qty;

    @Column(name = "status", nullable = false, length = 16)
    private String status; // PENDING, CONFIRMED, CANCELED

    @Column(name = "reason", length = 64)
    private String reason;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
