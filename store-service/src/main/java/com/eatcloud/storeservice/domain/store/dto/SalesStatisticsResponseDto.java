package com.eatcloud.storeservice.domain.store.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class SalesStatisticsResponseDto {

    private UUID storeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private SalesPeriodSummaryResponseDto summary;
    private List<DailySalesResponseDto> dailySales;
    private List<MenuSalesRankingResponseDto> topMenus;

    @Builder
    public SalesStatisticsResponseDto(UUID storeId, LocalDate startDate, LocalDate endDate,
                                      SalesPeriodSummaryResponseDto summary,
                                      List<DailySalesResponseDto> dailySales,
                                      List<MenuSalesRankingResponseDto> topMenus) {
        this.storeId = Objects.requireNonNull(storeId, "storeId cannot be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate cannot be null");
        this.endDate = Objects.requireNonNull(endDate, "endDate cannot be null");
        this.summary = Objects.requireNonNull(summary, "summary cannot be null");
        this.dailySales = dailySales != null ? List.copyOf(dailySales) : Collections.emptyList();
        this.topMenus = topMenus != null ? List.copyOf(topMenus) : Collections.emptyList();
    }
}
