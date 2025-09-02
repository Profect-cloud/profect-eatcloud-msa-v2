package com.eatcloud.storeservice.domain.store.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class StoreSearchRequestDto {
    private UUID categoryId;
    private double userLat;
    private double userLon;
    private double distanceKm = 3.0;
}
