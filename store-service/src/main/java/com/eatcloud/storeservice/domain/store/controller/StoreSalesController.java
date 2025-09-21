package com.eatcloud.storeservice.domain.store.controller;

import com.eatcloud.autoresponse.core.ApiResponse;
import com.eatcloud.logging.annotation.Loggable;
import com.eatcloud.storeservice.domain.store.dto.DailySalesResponseDto;
import com.eatcloud.storeservice.domain.store.dto.MenuSalesRankingResponseDto;
import com.eatcloud.storeservice.domain.store.dto.SalesPeriodSummaryResponseDto;
import com.eatcloud.storeservice.domain.store.dto.SalesStatisticsResponseDto;
import com.eatcloud.storeservice.domain.store.service.StoreSalesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores/sales")
@PreAuthorize("hasRole('MANAGER')")
@Tag(name = "5-3.Store Sales Statistics", description = "가게 매출 통계 API")
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public class StoreSalesController {

	private final StoreSalesService storeSalesService;

	public StoreSalesController(StoreSalesService storeSalesService) {
		this.storeSalesService = storeSalesService;
	}

	@Operation(summary = "일별 매출 조회", description = "특정 기간의 일별 매출 데이터를 조회합니다.")
	@GetMapping("/{storeId}/daily")
	public ApiResponse<List<DailySalesResponseDto>> getDailySales(
		@PathVariable UUID storeId,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
		@AuthenticationPrincipal UserDetails userDetails) {

		UUID managerId = getManagerUuid(userDetails);
		List<DailySalesResponseDto> salesData = storeSalesService.getDailySales(storeId, startDate, endDate, managerId);
		return ApiResponse.success(salesData);
	}

	@Operation(summary = "메뉴별 매출 순위", description = "특정 기간의 메뉴별 매출 순위를 조회합니다.")
	@GetMapping("/{storeId}/menu-ranking")
	public ApiResponse<List<MenuSalesRankingResponseDto>> getMenuSalesRanking(
		@PathVariable UUID storeId,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
		@RequestParam(defaultValue = "10") int limit,
		@AuthenticationPrincipal UserDetails userDetails) {

		UUID managerId = getManagerUuid(userDetails);
		List<MenuSalesRankingResponseDto> ranking = storeSalesService.getMenuSalesRanking(storeId, startDate, endDate,
			limit, managerId);
		return ApiResponse.success(ranking);
	}

	@Operation(summary = "기간별 매출 요약", description = "특정 기간의 매출 요약 정보를 조회합니다.")
	@GetMapping("/{storeId}/summary")
	public ResponseEntity<SalesPeriodSummaryResponseDto> getSalesSummary(
		@PathVariable UUID storeId,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
		@AuthenticationPrincipal UserDetails userDetails) {

		UUID managerId = getManagerUuid(userDetails);
		SalesPeriodSummaryResponseDto summary = storeSalesService.getSalesSummary(storeId, startDate, endDate,
			managerId);
		return ResponseEntity.ok(summary);
	}

	@Operation(summary = "통합 매출 통계", description = "가게의 통합 매출 통계 정보를 조회합니다.")
	@GetMapping("/{storeId}/statistics")
	public ResponseEntity<SalesStatisticsResponseDto> getSalesStatistics(
		@PathVariable UUID storeId,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
		@AuthenticationPrincipal UserDetails userDetails) {

		UUID managerId = getManagerUuid(userDetails);
		SalesStatisticsResponseDto statistics = storeSalesService.getSalesStatistics(storeId, startDate, endDate,
			managerId);
		return ResponseEntity.ok(statistics);
	}

	private UUID getManagerUuid(UserDetails userDetails) {
		return UUID.fromString(userDetails.getUsername());
	}
}