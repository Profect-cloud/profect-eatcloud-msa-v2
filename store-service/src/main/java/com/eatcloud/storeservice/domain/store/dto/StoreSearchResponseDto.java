package com.eatcloud.storeservice.domain.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Builder
@Getter
@AllArgsConstructor
public class StoreSearchResponseDto {
    private UUID storeId;
    private String storeName;
    private String storeAddress;
    private Double storeLat;
    private Double storeLon;
    private Integer minCost;
    private Boolean openStatus;

    public static StoreSearchResponseDto of(
            UUID storeId, String storeName, String storeAddress,
            Double storeLat, Double storeLon, Integer minCost, Boolean openStatus
    ) {
        return StoreSearchResponseDto.builder()
                .storeId(storeId)
                .storeName(storeName)
                .storeAddress(storeAddress)
                .storeLat(storeLat)
                .storeLon(storeLon)
                .minCost(minCost)
                .openStatus(openStatus)
                .build();
    }
}



