package com.eatcloud.storeservice.domain.inventory.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StockResponseDto {
    private int available;
    private int reserved;
}
