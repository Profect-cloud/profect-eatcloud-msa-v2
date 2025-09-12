package com.eatcloud.orderservice.event;

import lombok.*;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequestCancelEvent {
    
    private UUID orderId;
    private String sagaId;
}
