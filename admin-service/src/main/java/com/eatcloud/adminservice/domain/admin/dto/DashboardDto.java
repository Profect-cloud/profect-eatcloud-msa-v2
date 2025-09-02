package com.eatcloud.adminservice.domain.admin.dto;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardDto {
	private Long totalCustomers;
	private Long totalStores;
	private Long totalCategories;
	private Long totalOrders;
	private Map<String, Long> additionalMetrics;
}
