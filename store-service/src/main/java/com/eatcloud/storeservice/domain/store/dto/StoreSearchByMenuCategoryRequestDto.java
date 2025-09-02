package com.eatcloud.storeservice.domain.store.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StoreSearchByMenuCategoryRequestDto {
    private String categoryCode;
    private double userLat;
    private double userLon;
    private double distanceKm = 3.0;
}
