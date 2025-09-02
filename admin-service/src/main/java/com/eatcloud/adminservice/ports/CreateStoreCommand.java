package com.eatcloud.adminservice.ports;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data @NoArgsConstructor @AllArgsConstructor
public class CreateStoreCommand {
    private UUID applicationId;     // 멱등 키
    private UUID managerId;
    private String storeName;
    private String storeAddress;
    private String storePhoneNumber;
    private Integer categoryId;
    private String description;

}

