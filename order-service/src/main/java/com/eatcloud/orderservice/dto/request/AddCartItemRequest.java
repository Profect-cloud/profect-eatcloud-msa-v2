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
public class AddCartItemRequest {
    private UUID menuId;
    private String menuName;
    private Integer quantity;
    private Integer price;
    private UUID storeId;
}
