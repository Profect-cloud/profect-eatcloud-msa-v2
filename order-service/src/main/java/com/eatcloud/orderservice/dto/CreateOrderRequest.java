package com.eatcloud.orderservice.dto;

import com.eatcloud.orderservice.entity.OrderType;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {
    
    private UUID customerId;
    private UUID storeId;
    private Integer totalAmount;
    private Integer pointsToUse;
    private OrderType orderType;
    private String deliveryAddress;
    private List<OrderItemRequest> orderItems;
    
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemRequest {
        private UUID menuId;
        private String menuName;
        private Integer quantity;
        private Integer unitPrice;
        private String options;
    }
} 