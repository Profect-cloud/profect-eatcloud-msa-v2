package com.eatcloud.storeservice.domain.store.dto;

import lombok.*;

import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class StoreUpdateRequestDto {
    private String storeName;
    private String storeAddress;
    private String phoneNumber;
    private Integer minCost;
    private String description;
    private Double storeLat;
    private Double storeLon;
    private UUID categoryId;
    private LocalTime openTime;
    private LocalTime closeTime;
}
