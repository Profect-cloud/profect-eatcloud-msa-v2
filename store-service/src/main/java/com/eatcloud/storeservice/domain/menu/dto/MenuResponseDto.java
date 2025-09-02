package com.eatcloud.storeservice.domain.menu.dto;

import com.eatcloud.storeservice.domain.menu.entity.Menu;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuResponseDto {
    private UUID id;
    private int menuNum;
    private String menuName;
    private String menuCategoryCode;
    private BigDecimal price;
    private String description;
    private Boolean isAvailable;
    private String imageUrl;

    public static MenuResponseDto from(Menu menu) {
        return MenuResponseDto.builder()
                .id(menu.getId())
                .menuNum(menu.getMenuNum())
                .menuName(menu.getMenuName())
                .menuCategoryCode(menu.getMenuCategoryCode())
                .price(menu.getPrice())
                .description(menu.getDescription())
                .isAvailable(menu.getIsAvailable())
                .imageUrl(menu.getImageUrl())
                .build();
    }
}
