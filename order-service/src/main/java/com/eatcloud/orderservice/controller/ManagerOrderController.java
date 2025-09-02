package com.eatcloud.orderservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.eatcloud.orderservice.dto.request.AdminOrderCompleteRequestDto;
import com.eatcloud.orderservice.dto.request.AdminOrderConfirmRequestDto;
import com.eatcloud.orderservice.dto.response.AdminOrderResponseDto;
import com.eatcloud.orderservice.service.AdminOrderService;

@RestController
@RequestMapping("/orders/admin")
@RequiredArgsConstructor
@Slf4j
public class ManagerOrderController {

	private final AdminOrderService adminOrderService;

	@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
	@PostMapping("/confirm")
	public ResponseEntity<AdminOrderResponseDto> confirmOrder(
		@RequestBody AdminOrderConfirmRequestDto request) {

		log.info("주문 수락 요청: orderId={}", request.getOrderId());

		try {
			AdminOrderResponseDto response = adminOrderService.confirmOrder(request.getOrderId());
			log.info("주문 수락 완료: orderId={}, orderNumber={}",
				response.getOrderId(), response.getOrderNumber());

			return ResponseEntity.ok(response);
		} catch (RuntimeException e) {
			log.error("주문 수락 실패: orderId={}, error={}", request.getOrderId(), e.getMessage());

			AdminOrderResponseDto errorResponse = AdminOrderResponseDto.builder()
				.orderId(request.getOrderId())
				.orderNumber(null)
				.orderStatus("ERROR")
				.message(e.getMessage())
				.build();

			return ResponseEntity.badRequest().body(errorResponse);
		}
	}

	@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
	@PostMapping("/complete")
	public ResponseEntity<AdminOrderResponseDto> completeOrder(
		@RequestBody AdminOrderCompleteRequestDto request) {

		log.info("주문 완료 요청: orderId={}", request.getOrderId());

		try {
			AdminOrderResponseDto response = adminOrderService.completeOrder(request.getOrderId());
			log.info("주문 완료 처리 완료: orderId={}, orderNumber={}",
				response.getOrderId(), response.getOrderNumber());

			return ResponseEntity.ok(response);
		} catch (RuntimeException e) {
			log.error("주문 완료 실패: orderId={}, error={}", request.getOrderId(), e.getMessage());

			AdminOrderResponseDto errorResponse = AdminOrderResponseDto.builder()
				.orderId(request.getOrderId())
				.orderNumber(null)
				.orderStatus("ERROR")
				.message(e.getMessage())
				.build();

			return ResponseEntity.badRequest().body(errorResponse);
		}
	}

	@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
	@GetMapping("/{orderId}/status")
	public ResponseEntity<AdminOrderResponseDto> getOrderStatus(@PathVariable UUID orderId) {

		log.info("주문 상태 조회 요청: orderId={}", orderId);

		try {
			AdminOrderResponseDto response = adminOrderService.getOrderStatus(orderId);
			log.info("주문 상태 조회 완료: orderId={}, status={}", orderId, response.getOrderStatus());

			return ResponseEntity.ok(response);
		} catch (RuntimeException e) {
			log.error("주문 상태 조회 실패: orderId={}, error={}", orderId, e.getMessage());

			AdminOrderResponseDto errorResponse = AdminOrderResponseDto.builder()
				.orderId(orderId)
				.orderNumber(null)
				.orderStatus("ERROR")
				.message(e.getMessage())
				.build();

			return ResponseEntity.badRequest().body(errorResponse);
		}
	}
}
