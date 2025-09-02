package com.eatcloud.storeservice.internal.dto;

import lombok.*;

import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CreateStoreCommand {
    private UUID applicationId;
    private UUID managerId;
    private String storeName;
    private String storeAddress;
    private String storePhoneNumber;
    private Integer storeCategoryId;
    private String description;


}
