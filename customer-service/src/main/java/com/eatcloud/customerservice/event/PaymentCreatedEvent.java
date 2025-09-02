package com.eatcloud.customerservice.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCreatedEvent {
    
    private UUID paymentId;
    private UUID orderId;
    private UUID customerId;
    private Integer totalAmount;
    private String paymentStatus;
    private String paymentMethod;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
} 