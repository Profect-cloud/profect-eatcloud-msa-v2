package com.eatcloud.adminservice.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(name = "CategoryDto", description = "공통 카테고리 DTO (Store/Mid/Menu 겸용)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDto {
	@Schema(description = "카테고리 ID", example = "1")
	private Integer id;

	@Schema(description = "카테고리 코드", example = "KOREAN / RICE / BIBIMBAP")
	private String code;

	@Schema(description = "카테고리 이름", example = "한식 / 밥 / 비빔밥")
	private String displayName;

	@Schema(description = "정렬 순서", example = "1")
	private Integer sortOrder;

	@Schema(description = "활성 여부", example = "true")
	@Builder.Default
	private Boolean isActive = true;

	@Schema(description = "(선택) 사용 매장 수", example = "123")
	private Integer totalStoreAmount;

	@Schema(description = "(Mid/Menu 전용) 상위 StoreCategory ID", example = "1")
	private Integer storeCategoryId;

	@Schema(description = "(Menu 전용) 상위 MidCategory ID", example = "10")
	private Integer midCategoryId;
}
