package com.eatcloud.storeservice.domain.store.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class MenuSalesRankingResponseDto {

    private UUID menuId;
    private String menuName;
    private Integer totalQuantitySold;
    private BigDecimal totalAmount;
    private BigDecimal averagePrice;
    private Integer ranking;

    @Builder
    public MenuSalesRankingResponseDto(UUID menuId, String menuName, Integer totalQuantitySold,
                                       BigDecimal totalAmount, BigDecimal averagePrice, Integer ranking) {
        this.menuId = Objects.requireNonNull(menuId, "menuId cannot be null");
        this.menuName = Objects.requireNonNull(menuName, "menuName cannot be null");
        this.totalQuantitySold = totalQuantitySold != null ? totalQuantitySold : 0;
        this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        this.averagePrice = averagePrice != null ? averagePrice : BigDecimal.ZERO;
        this.ranking = ranking != null ? ranking : 0;
    }
}