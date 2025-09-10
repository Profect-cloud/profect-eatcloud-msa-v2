package com.eatcloud.orderservice.event;

import lombok.*;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointDeductionRequestEvent {
    
    private UUID orderId;
    private UUID customerId;
    private Integer pointsUsed;
    private String sagaId;
}
