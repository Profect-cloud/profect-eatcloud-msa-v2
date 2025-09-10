package com.eatcloud.paymentservice.event;

import lombok.*;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequestEvent {
    
    private UUID orderId;
    private UUID customerId;
    private Integer amount;
    private String sagaId;
}
