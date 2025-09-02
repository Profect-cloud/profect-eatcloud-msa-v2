package com.eatcloud.adminservice.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreDto {
	@Schema(description = "가게 UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
	private UUID storeId;

	@Schema(description = "가게 이름", example = "맛있는 분식집")
	private String storeName;

	@Schema(description = "카테고리 Id", example = "7f9f9f9f-1234-5678-9abc-def012345678")
	private Integer categoryId;

	@Schema(description = "최소 주문 금액", example = "12000")
	private Integer minCost;

	@Schema(description = "가게 설명", example = "신선한 재료만 사용합니다.")
	private String description;

	@Schema(description = "위도", example = "37.532600")
	private Double storeLat;

	@Schema(description = "경도", example = "127.024612")
	private Double storeLon;

	@Schema(description = "영업 중 여부", example = "true")
	private Boolean openStatus;

	@Schema(description = "오픈 시간", example = "09:00:00")
	private LocalTime openTime;

	@Schema(description = "마감 시간", example = "21:00:00")
	private LocalTime closeTime;
}
