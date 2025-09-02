package com.eatcloud.storeservice.domain.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(name = "StoreKeywordSearchRequest")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StoreKeywordSearchRequestDto {

    @Schema(description = "검색어(매장명/메뉴명/설명)", example = "비빔밥")
    private String q;

    @Schema(description = "상위 매장 카테고리 ID", example = "1")
    private Integer storeCategoryId;

    @Schema(description = "중간 카테고리 ID", example = "10")
    private Integer midCategoryId;

    @Schema(description = "메뉴 카테고리 ID", example = "100")
    private Integer menuCategoryId;

    @Schema(description = "메뉴 카테고리 코드 (ID 대신 코드 사용 시)", example = "BIBIMBAP")
    private String menuCategoryCode;

    @Schema(description = "페이지 번호(0-base)", example = "0")
    @Builder.Default private Integer page = 0;

    @Schema(description = "페이지 크기", example = "20")
    @Builder.Default private Integer size = 20;

    @Schema(description = "정렬 키: rating | createdAt", example = "rating")
    @Builder.Default private String sort = "createdAt";

    @Schema(description = "정렬 방향: ASC | DESC", example = "DESC")
    @Builder.Default private String direction = "DESC";
}
