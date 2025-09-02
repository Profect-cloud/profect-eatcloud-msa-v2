package com.eatcloud.orderservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderRequest {
    private UUID storeId;
    private String orderType;
    private Boolean usePoints;
    private Integer pointsToUse;

    private String deliveryAddress;
    private String deliveryRequests;

    private String pickupRequests;
}
