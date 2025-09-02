package com.eatcloud.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.eatcloud.autotime.BaseTimeEntity;

@Entity
@Table(name = "p_payments")
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends BaseTimeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_id")
    private UUID paymentId;
    
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    
    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount;
    
    @Column(name = "pg_transaction_id", length = 100)
    private String pgTransactionId;
    
    @Column(name = "approval_code", length = 50)
    private String approvalCode;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "card_info", columnDefinition = "jsonb")
    private Map<String, Object> cardInfo;
    
    @Column(name = "redirect_url", columnDefinition = "TEXT")
    private String redirectUrl;
    
    @Column(name = "receipt_url", columnDefinition = "TEXT")
    private String receiptUrl;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method", referencedColumnName = "code")
    private PaymentMethodCode paymentMethod;
    
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    @Column(name = "failed_at")
    private LocalDateTime failedAt;
    
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
        if (paymentStatus == null) {
            paymentStatus = PaymentStatus.PENDING;
        }
    }

    public void updateStatus(PaymentStatus status) {
        this.paymentStatus = status;
    }
    
    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }
    
    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }
    
    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
} 