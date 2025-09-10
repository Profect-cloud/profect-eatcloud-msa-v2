package com.eatcloud.storeservice.domain.inventory.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter
public class ReserveRequestDto {

    @NotNull private UUID orderId;
    @NotNull private UUID orderLineId;
    @NotNull private UUID menuId;
    @Min(1) private int qty;

}
