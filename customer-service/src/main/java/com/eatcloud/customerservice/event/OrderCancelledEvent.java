package com.eatcloud.customerservice.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCancelledEvent {
    
    private UUID orderId;
    private UUID customerId;
    private String cancelReason;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
}
