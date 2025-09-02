package com.eatcloud.storeservice.domain.menu.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuUpdateRequestDto {

    @NotNull(message = "메뉴 번호는 필수입니다.")
    @Min(value = 1, message = "메뉴 번호는 1 이상이어야 합니다.")
    private Integer menuNum;

    @NotBlank(message = "메뉴 이름은 필수입니다.")
    private String menuName;

    @NotBlank(message = "카테고리 코드는 필수입니다.")
    private String menuCategoryCode;

    @NotNull(message = "가격은 필수입니다.")
    @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
    private BigDecimal price;

    private String description;

    @NotNull(message = "판매 가능 여부는 필수입니다.")
    private Boolean isAvailable;

    private String imageUrl;

    @NotNull(message = "재고 무제한 여부는 필수입니다.")
    private Boolean isUnlimited;

    @NotNull(message = "재고 수량은 필수입니다.")
    @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다.")
    private Integer stockQuantity;
}
