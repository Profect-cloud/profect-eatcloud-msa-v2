package com.eatcloud.orderservice.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.eatcloud.orderservice.dto.CartItem;
import com.eatcloud.orderservice.dto.request.AddCartItemRequest;
import com.eatcloud.orderservice.dto.request.UpdateCartItemRequest;
import com.eatcloud.orderservice.dto.response.ApiResponse;
import com.eatcloud.orderservice.service.CartService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders/cart")
@Slf4j
public class CartController {

	private final CartService cartService;

	@PostMapping("/add")
	@ResponseStatus(HttpStatus.OK)
	public ApiResponse<String> addItem(
		@AuthenticationPrincipal Jwt jwt,
		@RequestBody AddCartItemRequest request) {

		UUID customerId = UUID.fromString(jwt.getSubject());
		cartService.addItem(customerId, request);
		return ApiResponse.success("장바구니에 상품이 추가되었습니다.");
	}

	@GetMapping
	@ResponseStatus(HttpStatus.OK)
	public ApiResponse<List<CartItem>> getCart(@AuthenticationPrincipal Jwt jwt) {
		UUID customerId = UUID.fromString(jwt.getSubject());
		List<CartItem> cartItems = cartService.getCart(customerId);
		return ApiResponse.success(cartItems);
	}

	@PatchMapping("/update")
	@ResponseStatus(HttpStatus.OK)
	public ApiResponse<String> updateQuantity(
		@AuthenticationPrincipal Jwt jwt,
		@RequestBody UpdateCartItemRequest request) {

		UUID customerId = UUID.fromString(jwt.getSubject());
		cartService.updateItemQuantity(customerId, request);
		return ApiResponse.success("장바구니 상품 수량이 변경되었습니다.");
	}

	@DeleteMapping("/delete/{menuId}")
	@ResponseStatus(HttpStatus.OK)
	public ApiResponse<String> removeItem(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID menuId) {

		UUID customerId = UUID.fromString(jwt.getSubject());
		cartService.removeItem(customerId, menuId);
		return ApiResponse.success("장바구니에서 상품이 삭제되었습니다.");
	}

	@DeleteMapping("/clear")
	@ResponseStatus(HttpStatus.OK)
	public ApiResponse<String> clearCart(@AuthenticationPrincipal Jwt jwt) {
		UUID customerId = UUID.fromString(jwt.getSubject());
		cartService.clearCart(customerId);
		return ApiResponse.success("장바구니가 비워졌습니다.");
	}

	@GetMapping("/batch-status")
	public ApiResponse<Map<String, Object>> getBatchStatus() {
		Map<String, Object> status = cartService.getBatchQueueStatus();
		return ApiResponse.success(status);
	}

	@PostMapping("/force-batch")
	public ApiResponse<String> forceBatchProcessing() {
		cartService.forceBatchProcessing();
		return ApiResponse.success("Batch processing triggered");
	}
}
