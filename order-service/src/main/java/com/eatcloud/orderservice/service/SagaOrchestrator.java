package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.dto.CartItem;
import com.eatcloud.orderservice.dto.OrderMenu;
import com.eatcloud.orderservice.dto.request.CreateOrderRequest;
import com.eatcloud.orderservice.dto.response.CreateOrderResponse;
import com.eatcloud.orderservice.entity.Order;
import com.eatcloud.orderservice.exception.ErrorCode;
import com.eatcloud.orderservice.exception.OrderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Saga 패턴 오케스트레이터
 * 분산 트랜잭션을 관리하고 보상 로직을 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrator {

    private final DistributedLockService lockService;
    private final CartService cartService;
    private final ExternalApiService externalApiService;
    private final OrderService orderService;
    private final RestTemplate restTemplate;

    /**
     * 주문 생성 Saga - 분산 트랜잭션 처리
     * 1. 분산락 획득
     * 2. 장바구니 조회 및 검증
     * 3. 주문 생성 (PENDING 상태)
     * 4. 포인트 예약 (customer-service)
     * 5. 결제 요청 (payment-service)
     */
    @Transactional
    public CreateOrderResponse createOrderSaga(UUID customerId, CreateOrderRequest request) {
        String sagaId = UUID.randomUUID().toString();
        SagaTransaction saga = new SagaTransaction(sagaId);

        log.info("Starting order saga: sagaId={}, customerId={}, storeId={}",
                sagaId, customerId, request.getStoreId());

        try {
            // 분산락 키 설정 (고객, 매장, 장바구니에 대한 락)
            String lockKey = "order:create:" + customerId;

            // 분산락을 사용한 트랜잭션 실행
            return lockService.executeWithLock(
                lockKey,
                5,  // 5초 대기
                30, // 30초 유지
                TimeUnit.SECONDS,
                () -> executeOrderCreation(customerId, request, saga)
            );

        } catch (Exception e) {
            log.error("Order saga failed: sagaId={}, error={}", sagaId, e.getMessage(), e);

            // Saga 보상 실행
            saga.compensate();

            if (e instanceof OrderException) {
                throw (OrderException) e;
            }
            throw new OrderException(ErrorCode.ORDER_PROCESSING,
                    "주문 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 실제 주문 생성 로직 실행
     */
    private CreateOrderResponse executeOrderCreation(UUID customerId, CreateOrderRequest request,
                                                     SagaTransaction saga) throws Exception {
        log.info("Executing order creation: customerId={}, storeId={}", customerId, request.getStoreId());

        // Step 1: 장바구니 조회
        log.info("Step 1: Fetching cart items for customer: {}", customerId);
        List<CartItem> cartItems = cartService.getCart(customerId);
        if (cartItems.isEmpty()) {
            throw new OrderException(ErrorCode.EMPTY_CART, "장바구니가 비어있습니다.");
        }

        // Step 2: 장바구니 아이템을 주문 메뉴로 변환
        log.info("Step 2: Converting cart items to order menu");
        List<OrderMenu> orderMenuList = cartItems.stream()
            .map(item -> OrderMenu.builder()
                .menuId(item.getMenuId())
                .menuName(item.getMenuName())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .build())
            .collect(Collectors.toList());

        // Step 3: 메뉴 가격 검증 및 업데이트
        log.info("Step 3: Validating menu prices");
        validateAndUpdateMenuPrices(orderMenuList);

        // Step 4: 주문 생성 (PENDING 상태)
        log.info("Step 4: Creating pending order");
        Order order = orderService.createPendingOrder(
            customerId,
            request.getStoreId(),
            orderMenuList,
            request.getOrderType(),
            request.getUsePoints(),
            request.getPointsToUse()
        );

        // 주문 취소를 위한 보상 추가
        saga.addCompensation("Cancel Order",
            () -> orderService.cancelOrder(order.getOrderId(), "Saga 실패로 인한 취소"));

        // Step 5: 포인트 예약 (사용하는 경우)
        if (Boolean.TRUE.equals(request.getUsePoints()) && request.getPointsToUse() > 0) {
            log.info("Step 5: Reserving points: {} points", request.getPointsToUse());
            String reservationId = reserveCustomerPoints(customerId, order.getOrderId(), request.getPointsToUse());

            // 포인트 예약 취소를 위한 보상 추가
            saga.addCompensation("Cancel Point Reservation",
                () -> cancelPointReservation(customerId, order.getOrderId()));
        }

        // Step 6: 결제 요청 생성
        log.info("Step 6: Creating payment request");
        String paymentUrl = createPaymentRequest(order.getOrderId(), customerId, order.getFinalPaymentAmount());

        // Step 7: 장바구니 비우기
        log.info("Step 7: Clearing cart for customer: {}", customerId);
        try {
            cartService.clearCart(customerId);
        } catch (Exception e) {
            // 장바구니 삭제 실패는 critical하지 않음
            log.warn("Failed to clear cart for customer: {}, continuing...", customerId, e);
        }

        // Saga 완료
        saga.complete();

        log.info("Order saga completed successfully: orderId={}, orderNumber={}",
                order.getOrderId(), order.getOrderNumber());

        // 응답 생성
        return CreateOrderResponse.builder()
            .orderId(order.getOrderId())
            .orderNumber(order.getOrderNumber())
            .totalPrice(order.getTotalPrice())
            .finalPaymentAmount(order.getFinalPaymentAmount())
            .orderStatus(order.getOrderStatusCode().getCode())
            .paymentUrl(paymentUrl)
            .message("주문이 성공적으로 생성되었습니다. 결제를 진행해주세요.")
            .build();
    }

    /**
     * 메뉴 가격 검증 및 업데이트
     */
    private void validateAndUpdateMenuPrices(List<OrderMenu> orderMenuList) {
        for (OrderMenu menu : orderMenuList) {
            try {
                Integer currentPrice = externalApiService.getMenuPrice(menu.getMenuId());
                menu.setPrice(currentPrice);
            } catch (Exception e) {
                log.error("Failed to get menu price for menuId: {}", menu.getMenuId(), e);
                throw new OrderException(ErrorCode.MENU_NOT_FOUND,
                    "메뉴 가격을 조회할 수 없습니다: " + menu.getMenuId());
            }
        }
    }

    /**
     * 고객 포인트 예약 (패스포트 기반 서비스 간 통신)
     */
    private String reserveCustomerPoints(UUID customerId, UUID orderId, Integer points) {
        try {
            String url = "http://customer-service/api/v1/customers/" + customerId + "/points/reserve";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Name", "order-service");
            headers.set("X-Customer-Id", customerId.toString()); // 사용자 컨텍스트 전달
            // TODO: 패스포트 토큰 발급 및 설정
            // headers.set("Authorization", "Bearer " + getServicePassportToken());
            
            Map<String, Object> requestBody = Map.of(
                "orderId", orderId.toString(),
                "points", points
            );
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                String reservationId = (String) response.getBody().get("reservationId");
                log.info("포인트 예약 성공: customerId={}, orderId={}, points={}, reservationId={}", 
                        customerId, orderId, points, reservationId);
                return reservationId;
            } else {
                throw new RuntimeException("포인트 예약 실패: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to reserve customer points: customerId={}, orderId={}, points={}", 
                     customerId, orderId, points, e);
            throw new OrderException(ErrorCode.POINT_DEDUCTION_FAILED, "포인트 예약에 실패했습니다.");
        }
    }

    /**
     * 포인트 예약 취소 (보상)
     */
    private void cancelPointReservation(UUID customerId, UUID orderId) {
        try {
            String url = "http://customer-service/api/v1/customers/" + customerId + "/points/cancel-reservation";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Name", "order-service");
            headers.set("X-Customer-Id", customerId.toString());
            // TODO: 패스포트 토큰 발급 및 설정
            
            Map<String, Object> requestBody = Map.of("orderId", orderId.toString());
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            restTemplate.postForEntity(url, request, String.class);
            log.info("포인트 예약 취소 완료: customerId={}, orderId={}", customerId, orderId);
        } catch (Exception e) {
            log.error("Failed to cancel point reservation: customerId={}, orderId={}", customerId, orderId, e);
        }
    }

    /**
     * 결제 완료 후 포인트 예약 처리 (실제 차감)
     */
    public void processPaymentCompletion(UUID orderId, UUID paymentId) {
        log.info("Processing payment completion: orderId={}, paymentId={}", orderId, paymentId);
        
        try {
            // 주문 정보 조회
            Order order = orderService.getOrderById(orderId);
            
            if (order.getPointsToUse() != null && order.getPointsToUse() > 0) {
                // 포인트 예약을 실제 차감으로 처리
                processPointReservation(order.getCustomerId(), orderId);
            }
            
            log.info("Payment completion processing finished: orderId={}, paymentId={}", orderId, paymentId);
            
        } catch (Exception e) {
            log.error("Failed to process payment completion: orderId={}, paymentId={}", orderId, paymentId, e);
            throw new RuntimeException("결제 완료 후처리 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 포인트 예약 처리 (실제 차감)
     */
    private void processPointReservation(UUID customerId, UUID orderId) {
        try {
            String url = "http://customer-service/api/v1/customers/" + customerId + "/points/process-reservation";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Name", "order-service");
            headers.set("X-Customer-Id", customerId.toString());
            // TODO: 패스포트 토큰 발급 및 설정
            
            Map<String, Object> requestBody = Map.of("orderId", orderId.toString());
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("포인트 예약 처리 완료: customerId={}, orderId={}", customerId, orderId);
            } else {
                throw new RuntimeException("포인트 예약 처리 실패: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to process point reservation: customerId={}, orderId={}", customerId, orderId, e);
            throw new RuntimeException("포인트 예약 처리 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 결제 요청 생성
     */
    private String createPaymentRequest(UUID orderId, UUID customerId, Integer amount) {
        try {
            String url = "http://payment-service/api/v1/payments/request";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Name", "order-service");
            headers.set("X-Customer-Id", customerId.toString());
            // TODO: 패스포트 토큰 발급 및 설정
            
            Map<String, Object> requestBody = Map.of(
                "orderId", orderId.toString(),
                "customerId", customerId.toString(),
                "amount", amount
            );
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                String paymentUrl = (String) response.getBody().get("redirectUrl");
                log.info("결제 요청 생성 성공: orderId={}, customerId={}, amount={}", orderId, customerId, amount);
                return paymentUrl;
            } else {
                throw new RuntimeException("결제 요청 생성 실패: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to create payment request: orderId={}, customerId={}, amount={}", 
                     orderId, customerId, amount, e);
            throw new OrderException(ErrorCode.ORDER_PROCESSING, "결제 요청 생성에 실패했습니다.");
        }
    }
}