package com.eatcloud.orderservice.entity;

import com.eatcloud.autotime.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "p_pickup_orders")
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickupOrder extends BaseTimeEntity {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "pickup_requests", columnDefinition = "TEXT")
    private String pickupRequests;

    @Column(name = "estimated_pickup_time")
    private LocalDateTime estimatedPickupTime;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "canceled_by", length = 100)
    private String canceledBy;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;
}
