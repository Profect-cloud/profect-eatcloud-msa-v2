package com.eatcloud.customerservice.event;

import lombok.*;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointDeductionResponseEvent {
    
    private UUID orderId;
    private UUID customerId;
    private Integer pointsUsed;
    private String sagaId;
    private boolean success;
    private String message;
}
