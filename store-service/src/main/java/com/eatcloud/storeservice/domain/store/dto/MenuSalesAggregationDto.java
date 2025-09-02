package com.eatcloud.storeservice.domain.store.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class MenuSalesAggregationDto {
    private UUID menuId;
    private String menuName;
    private Long totalQuantitySold;
    private BigDecimal totalAmount;

    public MenuSalesAggregationDto(UUID menuId, String menuName, Long totalQuantitySold, BigDecimal totalAmount) {
        this.menuId = Objects.requireNonNull(menuId, "menuId cannot be null");
        this.menuName = Objects.requireNonNull(menuName, "menuName cannot be null");
        this.totalQuantitySold = totalQuantitySold != null ? totalQuantitySold : 0L;
        this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
    }

    public BigDecimal getAveragePrice() {
        if (totalQuantitySold == null || totalQuantitySold == 0) {
            return BigDecimal.ZERO;
        }
        if (totalAmount == null) {
            return BigDecimal.ZERO;
        }
        return totalAmount.divide(BigDecimal.valueOf(totalQuantitySold), 2, RoundingMode.HALF_UP);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        MenuSalesAggregationDto that = (MenuSalesAggregationDto) obj;
        return Objects.equals(menuId, that.menuId) &&
                Objects.equals(menuName, that.menuName) &&
                Objects.equals(totalQuantitySold, that.totalQuantitySold) &&
                Objects.equals(totalAmount, that.totalAmount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(menuId, menuName, totalQuantitySold, totalAmount);
    }

    @Override
    public String toString() {
        return "MenuSalesAggregationDto{" +
                "menuId=" + menuId +
                ", menuName='" + menuName + '\'' +
                ", totalQuantitySold=" + totalQuantitySold +
                ", totalAmount=" + totalAmount +
                '}';
    }
}