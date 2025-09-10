package com.eatcloud.orderservice.event;

import lombok.*;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointReservationCancelResponseEvent {
    
    private UUID orderId;
    private UUID customerId;
    private String sagaId;
    private boolean success;
    private String errorMessage;
}
