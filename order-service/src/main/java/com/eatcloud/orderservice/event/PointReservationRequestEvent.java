package com.eatcloud.orderservice.event;

import lombok.*;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointReservationRequestEvent {
    
    private UUID orderId;
    private UUID customerId;
    private Integer points;
    private String sagaId;
}
