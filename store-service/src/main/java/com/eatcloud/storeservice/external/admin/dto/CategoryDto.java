package com.eatcloud.storeservice.external.admin.dto;

import lombok.Data;

@Data
public class CategoryDto {
    private Integer id;
    private String code;
    private String displayName;
    private Integer sortOrder;
    private Boolean isActive;
}
