package com.eatcloud.storeservice.domain.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StoreRegisterRequestDto {
	private String storeName;
	private String storeAddress;
	private String storePhoneNumber;
	private Integer categoryId;
	private String description;
}