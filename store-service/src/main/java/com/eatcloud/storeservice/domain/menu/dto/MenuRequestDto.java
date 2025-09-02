package com.eatcloud.storeservice.domain.menu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MenuRequestDto {

    @Min(value = 1, message = "메뉴 번호는 1 이상이어야 합니다.")
    private int menuNum;

    @NotBlank(message = "메뉴 이름은 필수입니다.")
    private String menuName;

    @NotBlank(message = "카테고리 코드는 필수입니다.")
    private String menuCategoryCode;

    @NotNull(message = "가격은 필수입니다.")
    @DecimalMin(value = "0.0", inclusive = false, message = "가격은 0보다 커야 합니다.")
    private BigDecimal price;

    private String description;

    private Boolean isAvailable;

    private String imageUrl;
}


