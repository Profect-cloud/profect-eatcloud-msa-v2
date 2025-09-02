package com.eatcloud.storeservice.domain.store.dto;

import com.eatcloud.storeservice.domain.store.entity.DailyStoreSales;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class DailySalesResponseDto {

    private LocalDate saleDate;
    private UUID storeId;
    private Integer orderCount;
    private BigDecimal totalAmount;
    private BigDecimal averageOrderAmount;

    @Builder
    public DailySalesResponseDto(LocalDate saleDate, UUID storeId, Integer orderCount,
                                 BigDecimal totalAmount, BigDecimal averageOrderAmount) {
        this.saleDate = Objects.requireNonNull(saleDate, "saleDate cannot be null");
        this.storeId = Objects.requireNonNull(storeId, "storeId cannot be null");
        this.orderCount = orderCount != null ? orderCount : 0;
        this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        this.averageOrderAmount = averageOrderAmount != null ? averageOrderAmount : BigDecimal.ZERO;
    }

    public static DailySalesResponseDto from(DailyStoreSales dailyStoreSales) {
        Objects.requireNonNull(dailyStoreSales, "dailyStoreSales cannot be null");

        BigDecimal averageAmount = calculateAverageAmount(
                dailyStoreSales.getTotalAmount(),
                dailyStoreSales.getOrderCount()
        );

        return DailySalesResponseDto.builder()
                .saleDate(dailyStoreSales.getSaleDate())
                .storeId(dailyStoreSales.getStoreId())
                .orderCount(dailyStoreSales.getOrderCount())
                .totalAmount(dailyStoreSales.getTotalAmount())
                .averageOrderAmount(averageAmount)
                .build();
    }

    private static BigDecimal calculateAverageAmount(BigDecimal totalAmount, Integer orderCount) {
        if (orderCount == null || orderCount <= 0 || totalAmount == null) {
            return BigDecimal.ZERO;
        }
        return totalAmount.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
    }
}