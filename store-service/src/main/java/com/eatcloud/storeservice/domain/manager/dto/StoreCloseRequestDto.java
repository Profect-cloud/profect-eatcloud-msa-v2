package com.eatcloud.storeservice.domain.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StoreCloseRequestDto {
    private String storeName;
    private String storeAddress;
    private String storePhoneNumber;
    private UUID categoryId;
    private String description;
}