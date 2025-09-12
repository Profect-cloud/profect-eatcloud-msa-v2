package com.eatcloud.orderservice.event;

import lombok.*;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequestResponseEvent {
    
    private UUID orderId;
    private UUID customerId;
    private String sagaId;
    private String paymentUrl;
    private boolean success;
    private String errorMessage;
}
