package com.eatcloud.storeservice.domain.inventory.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter
public class AdjustRequestDto {
    @NotNull private UUID menuId;
    /** +면 증량, -면 감량 */
    @Min(value = -1) private int delta;
}
