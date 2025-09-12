package com.eatcloud.orderservice.event;

import lombok.*;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequestCancelResponseEvent {
    
    private UUID orderId;
    private String sagaId;
    private boolean success;
    private String errorMessage;
}
