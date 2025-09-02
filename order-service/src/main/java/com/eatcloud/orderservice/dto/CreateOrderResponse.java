package com.eatcloud.orderservice.dto;

import com.eatcloud.orderservice.entity.OrderStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderResponse {
    
    private UUID orderId;
    private UUID customerId;
    private UUID storeId;
    private Integer totalAmount;
    private Integer finalAmount;
    private Integer pointsUsed;
    private OrderStatus orderStatus;
    private LocalDateTime orderDate;
    private LocalDateTime createdAt;
    private String message;
} 