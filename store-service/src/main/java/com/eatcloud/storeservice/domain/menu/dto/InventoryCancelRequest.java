package com.eatcloud.storeservice.domain.menu.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter
@NoArgsConstructor
public class InventoryCancelRequest {
    @NotNull private UUID orderId;
    @NotNull private UUID orderLineId;
    @NotNull private UUID menuId;
    @Min(1)  private int quantity;
    private String reason;
}
