package com.eatcloud.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

import com.eatcloud.autotime.BaseTimeEntity;

@Entity
@Table(name = "p_payment_requests")
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest extends BaseTimeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_request_id")
    private UUID paymentRequestId;
    
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    
    @Column(name = "pg_provider", nullable = false, length = 100)
    private String pgProvider;
    
    @Column(name = "request_payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String requestPayload;
    
    @Column(name = "redirect_url", columnDefinition = "text")
    private String redirectUrl;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentRequestStatus status;
    
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;
    
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
    
    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;
    
    @Column(name = "timeout_at")
    private LocalDateTime timeoutAt;
    
    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
        if (status == null) {
            status = PaymentRequestStatus.PENDING;
        }
    }
    
    public void updateStatus(PaymentRequestStatus status) {
        this.status = status;
        this.respondedAt = LocalDateTime.now();
    }
    
    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
    
    public void setTimeoutAt(LocalDateTime timeoutAt) {
        this.timeoutAt = timeoutAt;
    }
} 