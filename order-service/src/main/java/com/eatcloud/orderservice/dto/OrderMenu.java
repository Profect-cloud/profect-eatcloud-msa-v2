package com.eatcloud.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderMenu {
	private UUID menuId;
	private String menuName;
	private Integer quantity;
	private Integer price;
	
	public Integer getTotalPrice() {
		return price != null && quantity != null ? price * quantity : 0;
	}
}
