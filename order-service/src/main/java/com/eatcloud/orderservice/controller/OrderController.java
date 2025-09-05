package com.eatcloud.orderservice.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import com.eatcloud.orderservice.dto.request.OrderStatusUpdateRequest;
import com.eatcloud.orderservice.dto.request.CreateOrderRequest;
import com.eatcloud.orderservice.dto.request.PaymentCompleteRequest;
import com.eatcloud.orderservice.dto.request.PaymentFailedRequest;
import com.eatcloud.orderservice.dto.response.CreateOrderResponse;
import com.eatcloud.orderservice.dto.response.ApiResponse;
import com.eatcloud.orderservice.entity.Order;
import com.eatcloud.orderservice.exception.OrderException;
import com.eatcloud.orderservice.service.OrderService;
import com.eatcloud.orderservice.service.SagaOrchestrator;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

	private final OrderService orderService;
	private final SagaOrchestrator sagaOrchestrator;

	@PostMapping
	public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(
			@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
			@RequestBody CreateOrderRequest request) {

		try {
			UUID customerId = UUID.fromString(jwt.getSubject());

			String bearerToken = null;
			if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
				bearerToken = authorizationHeader.substring(7);
			}
			
			CreateOrderResponse response = orderService.createOrderFromCartSimple(customerId, request, bearerToken);
			return ResponseEntity.ok(ApiResponse.success(response));
		} catch (IllegalArgumentException e) {
			log.error("Invalid JWT subject format: {}", jwt != null ? jwt.getSubject() : "null");
			return ResponseEntity.badRequest()
				.body(ApiResponse.error("유효하지 않은 사용자 ID입니다."));
		}
	}

	@GetMapping("/{orderId}")
	public ResponseEntity<Map<String, Object>> getOrder(@PathVariable UUID orderId) {
		Map<String, Object> response = new HashMap<>();

		try {
			Optional<Order> order = orderService.findById(orderId);

			if (order.isPresent()) {
				Order foundOrder = order.get();
				response.put("orderId", foundOrder.getOrderId());
				response.put("orderNumber", foundOrder.getOrderNumber());
				response.put("customerId", foundOrder.getCustomerId());
				response.put("storeId", foundOrder.getStoreId());
				response.put("paymentId", foundOrder.getPaymentId());
				response.put("orderMenuList", foundOrder.getOrderMenuList());
				response.put("orderStatus", foundOrder.getOrderStatusCode().getCode());
				response.put("orderType", foundOrder.getOrderTypeCode().getCode());
				response.put("createdAt", foundOrder.getCreatedAt());
				response.put("message", "주문 조회 성공");

				return ResponseEntity.ok(response);
			} else {
				response.put("error", "주문을 찾을 수 없습니다.");
				return ResponseEntity.notFound().build();
			}

		} catch (Exception e) {
			response.put("error", "주문 조회 중 오류가 발생했습니다: " + e.getMessage());
			return ResponseEntity.internalServerError().body(response);
		}
	}

	@GetMapping("/number/{orderNumber}")
	public ResponseEntity<Map<String, Object>> getOrderByNumber(@PathVariable String orderNumber) {
		Map<String, Object> response = new HashMap<>();

		try {
			Optional<Order> order = orderService.findOrderByNumber(orderNumber);

			if (order.isPresent()) {
				Order foundOrder = order.get();
				response.put("orderId", foundOrder.getOrderId());
				response.put("orderNumber", foundOrder.getOrderNumber());
				response.put("customerId", foundOrder.getCustomerId());
				response.put("storeId", foundOrder.getStoreId());
				response.put("paymentId", foundOrder.getPaymentId());
				response.put("orderMenuList", foundOrder.getOrderMenuList());
				response.put("orderStatus", foundOrder.getOrderStatusCode().getCode());
				response.put("orderType", foundOrder.getOrderTypeCode().getCode());
				response.put("createdAt", foundOrder.getCreatedAt());
				response.put("message", "주문 조회 성공");

				return ResponseEntity.ok(response);
			} else {
				response.put("error", "주문을 찾을 수 없습니다.");
				return ResponseEntity.notFound().build();
			}

		} catch (Exception e) {
			response.put("error", "주문 조회 중 오류가 발생했습니다: " + e.getMessage());
			return ResponseEntity.internalServerError().body(response);
		}
	}

	@PreAuthorize("hasRole('ADMIN')")
	@GetMapping("/customers/{customerId}")
	public ResponseEntity<List<Order>> getCustomerOrders(
			@PathVariable UUID customerId) {
		return ResponseEntity.ok(orderService.findOrdersByCustomer(customerId));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@GetMapping("/customers/{customerId}/orders/{orderId}")
	public ResponseEntity<Order> getCustomerOrderDetail(
			@PathVariable UUID customerId,
			@PathVariable UUID orderId) {
		return ResponseEntity.ok(orderService.findOrderByCustomerAndOrderId(customerId, orderId));
	}

	@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
	@GetMapping("/stores/{storeId}")
	public ResponseEntity<List<Order>> getStoreOrders(
			@PathVariable UUID storeId) {
		return ResponseEntity.ok(orderService.findOrdersByStore(storeId));
	}

	@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
	@GetMapping("/stores/{storeId}/orders/{orderId}")
	public ResponseEntity<Order> getStoreOrderDetail(
			@PathVariable UUID orderId,
			@PathVariable UUID storeId) {
		return ResponseEntity.ok(orderService.findOrderByStoreAndOrderId(storeId, orderId));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@PatchMapping("/{orderId}/status")
	public ResponseEntity<Void> updateOrderStatus(
			@PathVariable UUID orderId,
			@RequestBody @Valid OrderStatusUpdateRequest request) {
		orderService.updateOrderStatus(orderId, request.getStatusCode());
		return ResponseEntity.noContent().build();
	}


	@PostMapping("/{orderId}/payment/complete")
	public ResponseEntity<Map<String, Object>> completePayment(
			@PathVariable UUID orderId,
			@RequestBody @Valid PaymentCompleteRequest request,
			@RequestHeader(value = "X-Service-Name", required = false) String serviceName) {

		Map<String, Object> response = new HashMap<>();

		try {
			if (!"payment-service".equals(serviceName)) {
				log.warn("Unauthorized payment completion attempt from: {}", serviceName);
				response.put("error", "Unauthorized service");
				return ResponseEntity.status(403).body(response);
			}

			log.info("결제 완료 콜백 수신: orderId={}, paymentId={}", orderId, request.getPaymentId());

			// 1. 주문 상태 업데이트
			orderService.completePayment(orderId, request.getPaymentId());
			
			// 2. 오케스트레이터를 통한 후속 처리 (포인트 실제 차감)
			sagaOrchestrator.processPaymentCompletion(orderId, request.getPaymentId());

			response.put("message", "주문 결제 완료 처리되었습니다");
			response.put("orderId", orderId);
			response.put("paymentId", request.getPaymentId());

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("결제 완료 처리 중 오류: orderId={}, paymentId={}", orderId, request.getPaymentId(), e);
			response.put("error", "결제 완료 처리 중 오류가 발생했습니다: " + e.getMessage());
			return ResponseEntity.internalServerError().body(response);
		}
	}

	@PostMapping("/{orderId}/payment/failed")
	public ResponseEntity<Map<String, Object>> failPayment(
			@PathVariable UUID orderId,
			@RequestBody @Valid PaymentFailedRequest request,
			@RequestHeader(value = "X-Service-Name", required = false) String serviceName) {

		Map<String, Object> response = new HashMap<>();

		try {
			if (!"payment-service".equals(serviceName)) {
				log.warn("Unauthorized payment failure attempt from: {}", serviceName);
				response.put("error", "Unauthorized service");
				return ResponseEntity.status(403).body(response);
			}

			log.info("결제 실패 콜백 수신: orderId={}, reason={}", orderId, request.getFailureReason());

			orderService.failPayment(orderId, request.getFailureReason());

			response.put("message", "주문 결제 실패 처리되었습니다");
			response.put("orderId", orderId);
			response.put("failureReason", request.getFailureReason());

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("결제 실패 처리 중 오류: orderId={}, reason={}", orderId, request.getFailureReason(), e);
			response.put("error", "결제 실패 처리 중 오류가 발생했습니다: " + e.getMessage());
			return ResponseEntity.internalServerError().body(response);
		}
	}

	@PostMapping("/{orderId}/payment/cancel")
	public ResponseEntity<Map<String, Object>> cancelPayment(
			@PathVariable UUID orderId,
			@RequestHeader(value = "X-Service-Name", required = false) String serviceName) {

		Map<String, Object> response = new HashMap<>();

		try {
			if (!"payment-service".equals(serviceName)) {
				log.warn("Unauthorized payment cancellation attempt from: {}", serviceName);
				response.put("error", "Unauthorized service");
				return ResponseEntity.status(403).body(response);
			}

			log.info("결제 취소 콜백 수신: orderId={}", orderId);

			orderService.cancelOrder(orderId);

			response.put("message", "주문이 취소되었습니다");
			response.put("orderId", orderId);

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("결제 취소 처리 중 오류: orderId={}", orderId, e);
			response.put("error", "결제 취소 처리 중 오류가 발생했습니다: " + e.getMessage());
			return ResponseEntity.internalServerError().body(response);
		}
	}

	@PostMapping("/saga")
	public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrderWithSaga(
			@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
			@RequestBody @Valid CreateOrderRequest request) {

		try {
			UUID customerId = UUID.fromString(jwt.getSubject());
			log.info("Creating order with Saga: customerId={}, storeId={}", customerId, request.getStoreId());

			CreateOrderResponse response = sagaOrchestrator.createOrderSaga(customerId, request);

			return ResponseEntity.ok(
					ApiResponse.<CreateOrderResponse>builder()
							.success(true)
							.message("주문이 성공적으로 생성되었습니다 (Saga)")
							.data(response)
							.build()
			);

		} catch (OrderException e) {
			log.error("Saga order creation failed", e);
			return ResponseEntity.badRequest().body(
					ApiResponse.<CreateOrderResponse>builder()
							.success(false)
							.message(e.getMessage())
							.build()
			);
		} catch (Exception e) {
			log.error("Unexpected error during saga order creation", e);
			return ResponseEntity.internalServerError().body(
					ApiResponse.<CreateOrderResponse>builder()
							.success(false)
							.message("주문 처리 중 예상치 못한 오류가 발생했습니다.")
							.build()
			);
		}
	}
}
