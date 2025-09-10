package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.dto.CartItem;
import com.eatcloud.orderservice.dto.OrderMenu;
import com.eatcloud.orderservice.dto.request.CreateOrderRequest;
import com.eatcloud.orderservice.dto.response.CreateOrderResponse;
import com.eatcloud.orderservice.entity.Order;
import com.eatcloud.orderservice.event.PointReservationRequestEvent;
import com.eatcloud.orderservice.event.PointReservationResponseEvent;
import com.eatcloud.orderservice.event.PointReservationCancelEvent;
import com.eatcloud.orderservice.event.PointReservationCancelResponseEvent;
import com.eatcloud.orderservice.event.PaymentRequestEvent;
import com.eatcloud.orderservice.event.PaymentRequestResponseEvent;
import com.eatcloud.orderservice.event.PaymentRequestCancelEvent;
import com.eatcloud.orderservice.event.PaymentRequestCancelResponseEvent;
import com.eatcloud.orderservice.exception.ErrorCode;
import com.eatcloud.orderservice.exception.OrderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Backoff;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
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
    private final OrderService orderService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PointReservationResponseEventConsumer pointReservationResponseEventConsumer;
    private final PointReservationCancelResponseEventConsumer pointReservationCancelResponseEventConsumer;
    private final PaymentRequestResponseEventConsumer paymentRequestResponseEventConsumer;
    private final PaymentRequestCancelResponseEventConsumer paymentRequestCancelResponseEventConsumer;

    /**
     * 주문 생성 Saga - 분산 트랜잭션 처리
     * 1. 분산락 획득
     * 2. 장바구니 조회 및 검증
     * 3. 주문 생성 (PENDING 상태)
     * 4. 포인트 예약 (customer-service)
     * 5. 결제 요청 (payment-service)
     */
    @Transactional
    public CreateOrderResponse createOrderSaga(UUID customerId, CreateOrderRequest request, String authorizationHeader) {
        log.info("=== createOrderSaga called ===");
        log.info("customerId: {}, storeId: {}, authorizationHeader: {}", customerId, request.getStoreId(), authorizationHeader != null ? "present" : "null");
        
        String sagaId = UUID.randomUUID().toString();
        Saga saga = new Saga(sagaId);
        
        try {
            log.info("Starting order saga: sagaId={}, customerId={}, storeId={}", sagaId, customerId, request.getStoreId());
            
            // 분산락 획득
            String lockKey = "order:create:" + customerId;
            if (!lockService.tryLock(lockKey, 5, TimeUnit.SECONDS)) {
                throw new OrderException(ErrorCode.ORDER_PROCESSING, "다른 주문이 진행 중입니다.");
            }
            
            try {
                // Step 1: 장바구니 조회
                log.info("Step 1: Fetching cart items for customer: {}", customerId);
                List<CartItem> cartItems = cartService.getCart(customerId);
                log.info("Cart items retrieved: count={}, items={}", cartItems.size(), cartItems);
                
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
                log.info("OrderMenuList created: size={}, items={}", orderMenuList.size(), orderMenuList);
                
                // Step 3: 메뉴 가격 검증 (선택사항)
                log.info("Step 3: Validating menu prices");
                // 가격 검증은 현재 비활성화
                
                // Step 4: 주문 생성
                log.info("Step 4: Creating order");
                Order order = orderService.createPendingOrder(
                    customerId,
                    request.getStoreId(),
                    orderMenuList,
                    request.getOrderType(),
                    request.getUsePoints(),
                    request.getPointsToUse()
                );
                log.info("Order created: orderId={}, orderMenuList size={}", order.getOrderId(), order.getOrderMenuList() != null ? order.getOrderMenuList().size() : "null");

                // 주문 취소를 위한 보상 추가
                saga.addCompensation("Cancel Order",
                    () -> orderService.cancelOrder(order.getOrderId(), "Saga 실패로 인한 취소"));

                // Step 5: 포인트 예약 (사용하는 경우)
                if (Boolean.TRUE.equals(request.getUsePoints()) && request.getPointsToUse() > 0) {
                    log.info("Step 5: Reserving points: {} points", request.getPointsToUse());
                    String reservationId = reserveCustomerPointsAsync(customerId, order.getOrderId(), request.getPointsToUse(), sagaId);

                    // 포인트 예약 취소를 위한 보상 추가
                    saga.addCompensation("Cancel Point Reservation",
                        () -> cancelPointReservationAsync(customerId, order.getOrderId(), sagaId));
                }

                // Step 6: 결제 요청 생성
                log.info("Step 6: Creating payment request");
                String paymentUrl = createPaymentRequestAsync(order.getOrderId(), customerId, order.getFinalPaymentAmount(), sagaId);

                // 결제 요청 취소를 위한 보상 추가
                saga.addCompensation("Cancel Payment Request",
                    () -> cancelPaymentRequestAsync(order.getOrderId(), sagaId));

                // Saga 성공 - 모든 보상 로직 제거
                saga.clearCompensations();

                log.info("Order saga completed successfully: orderId={}, paymentUrl={}", order.getOrderId(), paymentUrl);

                return CreateOrderResponse.builder()
                    .orderId(order.getOrderId())
                    .orderNumber(order.getOrderNumber())
                    .totalPrice(order.getTotalPrice())
                    .finalPaymentAmount(order.getFinalPaymentAmount())
                    .orderStatus(order.getOrderStatusCode().getCode())
                    .paymentUrl(paymentUrl)
                    .message("주문이 생성되었습니다.")
                    .build();

            } finally {
                lockService.unlock(lockKey);
            }
        } catch (Exception e) {
            log.error("=== Order saga failed ===");
            log.error("Exception type: {}, message: {}", e.getClass().getSimpleName(), e.getMessage());
            // 보상 로직 실행
            saga.executeCompensations();
            throw e;
        }
    }

    /**
     * 고객 포인트 예약 (Kafka 이벤트 기반)
     */
    private String reserveCustomerPointsAsync(UUID customerId, UUID orderId, Integer points, String sagaId) {
        try {
            // 포인트 예약 요청 이벤트 발행
            PointReservationRequestEvent requestEvent = PointReservationRequestEvent.builder()
                    .orderId(orderId)
                    .customerId(customerId)
                    .points(points)
                    .sagaId(sagaId)
                    .build();
            
            kafkaTemplate.send("point.reservation.request", requestEvent);
            log.info("포인트 예약 요청 이벤트 발행: customerId={}, orderId={}, points={}, sagaId={}", 
                    customerId, orderId, points, sagaId);
            
            // 응답 대기 (최대 10초)
            CompletableFuture<PointReservationResponseEvent> responseFuture = 
                    pointReservationResponseEventConsumer.waitForResponse(sagaId);
            
            PointReservationResponseEvent response = responseFuture.get(10, TimeUnit.SECONDS);
            
            if (response.isSuccess()) {
                log.info("포인트 예약 성공: customerId={}, orderId={}, points={}, reservationId={}", 
                        customerId, orderId, points, response.getReservationId());
                return response.getReservationId();
            } else {
                throw new RuntimeException("포인트 예약 실패: " + response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Failed to reserve customer points: customerId={}, orderId={}, points={}", 
                     customerId, orderId, points, e);
            throw new RestClientException("포인트 예약에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 포인트 예약 취소 (Kafka 이벤트 기반)
     */
    private void cancelPointReservationAsync(UUID customerId, UUID orderId, String sagaId) {
        try {
            // 포인트 예약 취소 이벤트 발행
            PointReservationCancelEvent cancelEvent = PointReservationCancelEvent.builder()
                    .orderId(orderId)
                    .customerId(customerId)
                    .sagaId(sagaId)
                    .build();
            
            kafkaTemplate.send("point.reservation.cancel", cancelEvent);
            log.info("포인트 예약 취소 이벤트 발행: customerId={}, orderId={}, sagaId={}", 
                    customerId, orderId, sagaId);
            
            // 응답 대기 (최대 5초)
            CompletableFuture<PointReservationCancelResponseEvent> responseFuture = 
                    pointReservationCancelResponseEventConsumer.waitForResponse(sagaId);
            
            PointReservationCancelResponseEvent response = responseFuture.get(5, TimeUnit.SECONDS);
            
            if (response.isSuccess()) {
                log.info("포인트 예약 취소 성공: customerId={}, orderId={}", customerId, orderId);
            } else {
                log.error("포인트 예약 취소 실패: customerId={}, orderId={}, error={}", 
                        customerId, orderId, response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Failed to cancel point reservation: customerId={}, orderId={}", customerId, orderId, e);
            // 취소 실패는 로그만 남기고 예외를 던지지 않음 (보상 로직이므로)
        }
    }

    /**
     * 결제 요청 생성 (Kafka 이벤트 기반)
     */
    private String createPaymentRequestAsync(UUID orderId, UUID customerId, Integer amount, String sagaId) {
        try {
            // 결제 요청 이벤트 발행
            PaymentRequestEvent requestEvent = PaymentRequestEvent.builder()
                    .orderId(orderId)
                    .customerId(customerId)
                    .amount(amount)
                    .sagaId(sagaId)
                    .build();
            
            kafkaTemplate.send("payment.request", requestEvent);
            log.info("결제 요청 이벤트 발행: orderId={}, customerId={}, amount={}, sagaId={}", 
                    orderId, customerId, amount, sagaId);
            
            // 응답 대기 (최대 10초)
            CompletableFuture<PaymentRequestResponseEvent> responseFuture = 
                    paymentRequestResponseEventConsumer.waitForResponse(sagaId);
            
            PaymentRequestResponseEvent response = responseFuture.get(10, TimeUnit.SECONDS);
            
            if (response.isSuccess()) {
                log.info("결제 요청 성공: orderId={}, customerId={}, amount={}, paymentUrl={}", 
                        orderId, customerId, amount, response.getPaymentUrl());
                return response.getPaymentUrl();
            } else {
                throw new RuntimeException("결제 요청 실패: " + response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Failed to create payment request: orderId={}, customerId={}, amount={}", 
                     orderId, customerId, amount, e);
            throw new RestClientException("결제 요청 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 결제 요청 취소 (Kafka 이벤트 기반)
     */
    private void cancelPaymentRequestAsync(UUID orderId, String sagaId) {
        try {
            // 결제 요청 취소 이벤트 발행
            PaymentRequestCancelEvent cancelEvent = PaymentRequestCancelEvent.builder()
                    .orderId(orderId)
                    .sagaId(sagaId)
                    .build();
            
            kafkaTemplate.send("payment.request.cancel", cancelEvent);
            log.info("결제 요청 취소 이벤트 발행: orderId={}, sagaId={}", orderId, sagaId);
            
            // 응답 대기 (최대 5초)
            CompletableFuture<PaymentRequestCancelResponseEvent> responseFuture = 
                    paymentRequestCancelResponseEventConsumer.waitForResponse(sagaId);
            
            PaymentRequestCancelResponseEvent response = responseFuture.get(5, TimeUnit.SECONDS);
            
            if (response.isSuccess()) {
                log.info("결제 요청 취소 성공: orderId={}", orderId);
            } else {
                log.error("결제 요청 취소 실패: orderId={}, error={}", orderId, response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Failed to cancel payment request: orderId={}", orderId, e);
            // 취소 실패는 로그만 남기고 예외를 던지지 않음 (보상 로직이므로)
        }
    }

    // 복구 메서드들 (이벤트 기반으로 변경되어 더 이상 필요 없음)

    /**
     * Saga 보상 로직을 관리하는 내부 클래스
     */
    private static class Saga {
        private final String sagaId;
        private final List<Runnable> compensations = new java.util.ArrayList<>();

        public Saga(String sagaId) {
            this.sagaId = sagaId;
        }

        public void addCompensation(String name, Runnable compensation) {
            log.info("보상 로직 추가: sagaId={}, name={}", sagaId, name);
            compensations.add(compensation);
        }

        public void executeCompensations() {
            log.info("보상 로직 실행 시작: sagaId={}, count={}", sagaId, compensations.size());
            for (int i = compensations.size() - 1; i >= 0; i--) {
                try {
                    compensations.get(i).run();
                    log.info("보상 로직 실행 완료: sagaId={}, index={}", sagaId, i);
                } catch (Exception e) {
                    log.error("보상 로직 실행 실패: sagaId={}, index={}", sagaId, i, e);
                }
            }
        }

        public void clearCompensations() {
            compensations.clear();
        }
    }
}
