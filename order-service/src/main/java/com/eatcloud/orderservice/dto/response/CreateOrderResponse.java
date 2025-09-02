package com.eatcloud.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderResponse {
    private UUID orderId;
    private String orderNumber;
    private Integer totalPrice;
    private Integer finalPaymentAmount;
    private String orderStatus;
    private String message;
}
