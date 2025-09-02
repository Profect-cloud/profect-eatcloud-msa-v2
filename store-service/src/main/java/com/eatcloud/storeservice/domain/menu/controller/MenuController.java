package com.eatcloud.storeservice.domain.menu.controller;

import com.eatcloud.autoresponse.core.ApiResponse;
import com.eatcloud.storeservice.domain.menu.dto.MenuResponseDto;
import com.eatcloud.storeservice.domain.menu.entity.Menu;
import com.eatcloud.storeservice.domain.menu.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/stores/{store_id}/menus")
@AllArgsConstructor
@Tag(name = "5-2. MenuController")
public class MenuController {
	private final MenuService menuService;

	@Operation(summary = "단일 매장 메뉴 리스트 조회")
	@GetMapping
	public ApiResponse<List<MenuResponseDto>> getMenus(@PathVariable UUID store_id) {
		List<Menu> menus = menuService.getMenusByStore(store_id);
		List<MenuResponseDto> response = menus.stream()
			.map(MenuResponseDto::from)
			.collect(Collectors.toList());

		return ApiResponse.success(response);
	}

	@Operation(summary = "메뉴 상세 조회")
	@GetMapping("/{menu_id}")
	public ApiResponse<MenuResponseDto> getMenuDetail(@PathVariable UUID store_id, @PathVariable UUID menu_id) {
		Menu menu = menuService.getMenuById(store_id, menu_id);
		return ApiResponse.success(MenuResponseDto.from(menu));
	}
}

