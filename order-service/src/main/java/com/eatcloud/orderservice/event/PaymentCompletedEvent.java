package com.eatcloud.orderservice.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCompletedEvent {
    
    private UUID orderId;
    private UUID customerId;
    private UUID paymentId;
    private Integer totalAmount;
    private Integer amount;
    private Integer pointsUsed;
    private String paymentMethod;
    private String transactionId;
    private LocalDateTime completedAt;
}