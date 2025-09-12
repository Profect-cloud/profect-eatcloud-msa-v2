package com.eatcloud.orderservice.event;

import lombok.*;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointReservationCancelEvent {
    
    private UUID orderId;
    private UUID customerId;
    private String sagaId;
}
