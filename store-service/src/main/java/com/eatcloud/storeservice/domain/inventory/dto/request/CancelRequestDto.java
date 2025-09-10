package com.eatcloud.storeservice.domain.inventory.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter
public class CancelRequestDto {
    @NotNull private UUID orderLineId;
    private String reason;
}
