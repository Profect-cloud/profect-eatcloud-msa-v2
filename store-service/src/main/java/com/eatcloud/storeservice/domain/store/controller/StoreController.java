package com.eatcloud.storeservice.domain.store.controller;

import com.eatcloud.autoresponse.core.ApiResponse;
import com.eatcloud.storeservice.domain.store.dto.StoreKeywordSearchRequestDto;
import com.eatcloud.storeservice.domain.store.dto.StoreSearchByMenuCategoryRequestDto;
import com.eatcloud.storeservice.domain.store.dto.StoreSearchRequestDto;
import com.eatcloud.storeservice.domain.store.dto.StoreSearchResponseDto;
import com.eatcloud.storeservice.domain.store.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/api/v1/stores")
@AllArgsConstructor

@Tag(name = "5-1. StoreController")
public class StoreController {

	private final StoreService storeService;

	@Operation(summary = "1. 매장 카테고리 별 거리기반 매장 조회")
	@GetMapping("/search/category")
	public ApiResponse<List<StoreSearchResponseDto>> searchStoresByCategoryAndDistance(
		@ModelAttribute StoreSearchRequestDto condition
	) {
		List<StoreSearchResponseDto> stores = storeService.searchStoresByCategoryAndDistance(condition);
		return ApiResponse.success(stores);
	}

	@Operation(summary = "2. 메뉴 카테고리 별 거리 기반 매장 검색")
	@GetMapping("/search/menu-category")
	public ApiResponse<List<StoreSearchResponseDto>> searchStoresByMenuCategoryAndDistance(
		@ModelAttribute StoreSearchByMenuCategoryRequestDto condition
	) {
		List<StoreSearchResponseDto> stores = storeService.searchStoresByMenuCategory(condition);
		return ApiResponse.success(stores);
	}

	@Operation(summary = "3. 키워드 + 카테고리 + 페이지네이션 + 정렬")
	@GetMapping("/search")
	public ApiResponse<Page<StoreSearchResponseDto>> searchByKeyword(
			@ModelAttribute StoreKeywordSearchRequestDto req
	) {
		return ApiResponse.success(storeService.searchStoresByKeyword(req));
	}

}