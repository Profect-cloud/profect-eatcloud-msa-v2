package com.eatcloud.paymentservice.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedEvent {
    
    private UUID orderId;
    private UUID customerId;
    private UUID storeId;
    private Integer totalAmount;
    private Integer finalAmount;
    private Integer pointsToUse;
    private String orderStatus;
    private String orderType;
    private LocalDateTime orderDate;
    private LocalDateTime createdAt;
    private List<OrderItemEvent> orderItems;
    
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemEvent {
        private UUID menuId;
        private String menuName;
        private Integer quantity;
        private Integer unitPrice;
        private String options;
    }
} 