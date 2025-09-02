package com.eatcloud.customerservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.eatcloud.autotime.BaseTimeEntity;

@Entity
@SQLRestriction("deleted_at is null")
@Table(name = "point_reservations")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointReservation extends BaseTimeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reservation_id")
    private UUID reservationId;
    
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    
    @Column(name = "points", nullable = false)
    private Integer points;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;
    
    @Column(name = "reserved_at", nullable = false)
    private LocalDateTime reservedAt;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @PrePersist
    protected void onCreate() {
        reservedAt = LocalDateTime.now();
        if (status == null) {
            status = ReservationStatus.RESERVED;
        }
    }
    
    public void process() {
        this.status = ReservationStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }
    
    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
        this.processedAt = LocalDateTime.now();
    }

    public boolean isReserved() {
        return ReservationStatus.RESERVED.equals(this.status);
    }
    
    public boolean isProcessed() {
        return ReservationStatus.PROCESSED.equals(this.status);
    }
    
    public boolean isCancelled() {
        return ReservationStatus.CANCELLED.equals(this.status);
    }
    
    public boolean canProcess() {
        return isReserved();
    }
    
    public boolean canCancel() {
        return isReserved();
    }
    
    @Override
    public String toString() {
        return "PointReservation{" +
                "reservationId=" + reservationId +
                ", customerId=" + customerId +
                ", orderId=" + orderId +
                ", points=" + points +
                ", status=" + status +
                ", reservedAt=" + reservedAt +
                ", processedAt=" + processedAt +
                '}';
    }
} 
