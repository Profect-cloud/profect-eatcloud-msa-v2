package com.eatcloud.storeservice.domain.inventory.dto.request;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter
public class ConfirmRequestDto {
    @NotNull private UUID orderLineId;
}
