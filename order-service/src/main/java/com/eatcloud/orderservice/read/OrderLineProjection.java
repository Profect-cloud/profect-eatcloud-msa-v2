package com.eatcloud.orderservice.read;

import jakarta.persistence.*; import lombok.*;
import java.time.LocalDateTime; import java.util.UUID;

@Entity @Table(name="order_line_projection")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderLineProjection {
    @Id @Column(name="order_line_id")
    private UUID orderLineId;

    @Column(name="order_id", nullable=false)
    private UUID orderId;

    @Column(name="menu_id", nullable=false)
    private UUID menuId;

    @Column(name="qty", nullable=false)
    private int qty;

    @Column(name="stock_status", nullable=false, length=32)
    private String stockStatus;

    @Column(name="updated_at", nullable=false)
    private LocalDateTime updatedAt;
}
