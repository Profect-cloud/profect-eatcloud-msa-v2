package com.eatcloud.storeservice.domain.store.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class SalesPeriodSummaryResponseDto {

    private UUID storeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalAmount;
    private Integer totalOrderCount;
    private BigDecimal averageDailyAmount;
    private DailySalesResponseDto bestSalesDay;
    private DailySalesResponseDto worstSalesDay;
    private Integer salesDays;

    @Builder
    public SalesPeriodSummaryResponseDto(UUID storeId, LocalDate startDate, LocalDate endDate,
                                         BigDecimal totalAmount, Integer totalOrderCount, BigDecimal averageDailyAmount,
                                         DailySalesResponseDto bestSalesDay, DailySalesResponseDto worstSalesDay,
                                         Integer salesDays) {
        this.storeId = Objects.requireNonNull(storeId, "storeId cannot be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate cannot be null");
        this.endDate = Objects.requireNonNull(endDate, "endDate cannot be null");
        this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        this.totalOrderCount = totalOrderCount != null ? totalOrderCount : 0;
        this.averageDailyAmount = averageDailyAmount != null ? averageDailyAmount : BigDecimal.ZERO;
        this.bestSalesDay = bestSalesDay;
        this.worstSalesDay = worstSalesDay;
        this.salesDays = salesDays != null ? salesDays : 0;
    }

    public static SalesPeriodSummaryResponseDto empty(UUID storeId, LocalDate startDate, LocalDate endDate) {
        return SalesPeriodSummaryResponseDto.builder()
                .storeId(storeId)
                .startDate(startDate)
                .endDate(endDate)
                .totalAmount(BigDecimal.ZERO)
                .totalOrderCount(0)
                .averageDailyAmount(BigDecimal.ZERO)
                .salesDays(0)
                .build();
    }
}