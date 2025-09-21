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
import com.eatcloud.orderservice.kafka.consumer.PaymentRequestCancelResponseEventConsumer;
import com.eatcloud.orderservice.kafka.consumer.PaymentRequestResponseEventConsumer;
import com.eatcloud.orderservice.kafka.consumer.PointReservationCancelResponseEventConsumer;
import com.eatcloud.orderservice.kafka.consumer.PointReservationResponseEventConsumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

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
    private final OutboxService outboxService;

    private final ExternalApiService externalApiService;

    /** orderId, menuId, index로 결정적 라인ID 생성 (UUID v5 스타일) */
    private UUID deriveLineId(UUID orderId, UUID menuId, int index) {
        String seed = orderId + ":" + menuId + ":" + index;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public CreateOrderResponse createOrderSaga(UUID customerId, CreateOrderRequest request, String authorizationHeader) {
        log.info("=== createOrderSaga called ===");
        log.info("customerId: {}, storeId: {}, authorizationHeader: {}", customerId, request.getStoreId(), authorizationHeader != null ? "present" : "null");
        
        String sagaId = UUID.randomUUID().toString();
        Saga saga = new Saga(sagaId);
        
        try {
            log.info("Starting order saga: sagaId={}, customerId={}, storeId={}", sagaId, customerId, request.getStoreId());

            String lockKey = "order:create:" + customerId;
            if (!lockService.tryLock(lockKey, 5, TimeUnit.SECONDS)) {
                throw new OrderException(ErrorCode.ORDER_PROCESSING, "다른 주문이 진행 중입니다.");
            }
            
            try {
                log.info("Step 1: Fetching cart items for customer: {}", customerId);
                List<CartItem> cartItems = cartService.getCart(customerId);
                log.info("Cart items retrieved: count={}, items={}", cartItems.size(), cartItems);
                
                if (cartItems.isEmpty()) {
                    throw new OrderException(ErrorCode.EMPTY_CART, "장바구니가 비어있습니다.");
                }

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
                
                // 메뉴 가격 검증

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

                // Outbox: OrderCreatedEvent 저장
                try {
                    com.eatcloud.orderservice.event.OrderCreatedEvent createdEvent =
                            com.eatcloud.orderservice.event.OrderCreatedEvent.builder()
                                    .orderId(order.getOrderId())
                                    .customerId(order.getCustomerId())
                                    .storeId(order.getStoreId())
                                    .totalAmount(order.getTotalPrice())
                                    .finalAmount(order.getFinalPaymentAmount())
                                    .pointsToUse(order.getPointsToUse())
                                    .orderItems(orderMenuList.stream()
                                            .map(m -> com.eatcloud.orderservice.event.OrderCreatedEvent.OrderItemEvent.builder()
                                                    .menuId(m.getMenuId())
                                                    .menuName(m.getMenuName())
                                                    .quantity(m.getQuantity())
                                                    .unitPrice(m.getPrice())
                                                    .build())
                                            .collect(java.util.stream.Collectors.toList()))
                                    .build();

                    outboxService.saveEvent(
                            "Order",
                            order.getOrderId().toString(),
                            "OrderCreatedEvent",
                            createdEvent,
                            outboxService.defaultHeaders(null, sagaId)
                    );
                    log.info("OrderCreatedEvent Outbox 기록 완료: orderId={}", order.getOrderId());
                } catch (Exception e) {
                    log.error("OrderCreatedEvent Outbox 기록 실패: orderId={}", order.getOrderId(), e);
                }

                saga.addCompensation("Cancel Order",
                    () -> orderService.cancelOrder(order.getOrderId(), "Saga 실패로 인한 취소"));

                if (Boolean.TRUE.equals(request.getUsePoints()) && request.getPointsToUse() > 0) {
                    log.info("Step 5: Reserving points: {} points", request.getPointsToUse());
                    String reservationId = reserveCustomerPointsAsync(customerId, order.getOrderId(), request.getPointsToUse(), sagaId);

                    saga.addCompensation("Cancel Point Reservation",
                        () -> cancelPointReservationAsync(customerId, order.getOrderId(), sagaId));
                }

                // === [INSERT #1] 라인별 재고 예약 Try-Fast (실패해도 주문은 계속 진행) ===
                for (int i = 0; i < order.getOrderMenuList().size(); i++) {
                    var line = order.getOrderMenuList().get(i);

                    UUID menuId = line.getMenuId();
                    int  qty    = line.getQuantity() != null ? line.getQuantity() : 0;

                    // ✅ 결정적 라인ID 생성 (orderId, menuId, index 기반)
                    UUID lineId = deriveLineId(order.getOrderId(), menuId, i);

                    boolean reserved = externalApiService.reserveInventory(
                            order.getOrderId(), lineId, menuId, qty, authorizationHeader);

                    if (!reserved) {
                        try {
                            // 선택: 표시만 PENDING_STOCK로 (메서드 없으면 생략 가능)
//                            orderService.markLineStockPending(lineId);
                        } catch (Exception ignore) {
                            log.info("markLineStockPending not available; continue. lineId={}", lineId);
                        }
                        log.info("Inventory reserve pending/failed. lineId={} menuId={} qty={}", lineId, menuId, qty);
                    }

                    // 보상 등록: 확정 이후 문제 생기면 반납
                    saga.addCompensation("Return Inventory after confirm",
                            () -> externalApiService.cancelInventoryAfterConfirm(
                                    lineId, "SAGA_COMPENSATION", authorizationHeader));
                }
// === [INSERT #1] 끝 ===



                log.info("Step 6: Creating payment request");
                String paymentUrl = createPaymentRequestAsync(order.getOrderId(), customerId, order.getFinalPaymentAmount(), sagaId);

                saga.addCompensation("Cancel Payment Request",
                    () -> cancelPaymentRequestAsync(order.getOrderId(), sagaId));

                /* === [INSERT #2] 결제요청 성공 → 라인별 재고 confirm 시도 === */
                for (int i = 0; i < order.getOrderMenuList().size(); i++) {
                    var line = order.getOrderMenuList().get(i);
                    UUID menuId = line.getMenuId();
                    UUID lineId = deriveLineId(order.getOrderId(), menuId, i);

                    boolean ok = externalApiService.confirmInventory(lineId, authorizationHeader);
                    if (!ok) {
                        log.warn("Confirm failed (will rely on compensation or TTL) lineId={}", lineId);
                        // 실패해도 주문 흐름은 계속. 스토어는 멱등 처리 + TTL/보상 이벤트로 수습.
                    }

                    // 보상은 이미 위쪽에서 "Return Inventory after confirm" 등록되어 있음
                    // cancelInventoryAfterConfirm(...) 호출이 등록되어 있으니 실패시 보상으로 롤백됨.
                }
                /* === [INSERT #2] 끝 === */

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

            saga.executeCompensations();
            throw e;
        }
    }

    private String reserveCustomerPointsAsync(UUID customerId, UUID orderId, Integer points, String sagaId) {
        try {
            PointReservationRequestEvent requestEvent = PointReservationRequestEvent.builder()
                    .orderId(orderId)
                    .customerId(customerId)
                    .points(points)
                    .sagaId(sagaId)
                    .build();
            
            kafkaTemplate.send("point.reservation.request", orderId.toString(), requestEvent);
            log.info("포인트 예약 요청 이벤트 발행: customerId={}, orderId={}, points={}, sagaId={}", 
                    customerId, orderId, points, sagaId);

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
        } catch (TimeoutException e) {
            log.warn("포인트 예약 타임아웃: customerId={}, orderId={}, points={}", 
                    customerId, orderId, points);
            throw new RuntimeException("포인트 예약 요청이 시간 초과되었습니다");
        } catch (Exception e) {
            log.error("포인트 예약 실패: customerId={}, orderId={}, points={}", 
                     customerId, orderId, points);
            throw new RuntimeException("포인트 예약에 실패했습니다: " + e.getMessage());
        }
    }

    private void cancelPointReservationAsync(UUID customerId, UUID orderId, String sagaId) {
        try {
            PointReservationCancelEvent cancelEvent = PointReservationCancelEvent.builder()
                    .orderId(orderId)
                    .customerId(customerId)
                    .sagaId(sagaId)
                    .build();
            
            kafkaTemplate.send("point.reservation.cancel", orderId.toString(), cancelEvent);
            log.info("포인트 예약 취소 이벤트 발행: customerId={}, orderId={}, sagaId={}", 
                    customerId, orderId, sagaId);

            CompletableFuture<PointReservationCancelResponseEvent> responseFuture = 
                    pointReservationCancelResponseEventConsumer.waitForResponse(sagaId);
            
            PointReservationCancelResponseEvent response = responseFuture.get(5, TimeUnit.SECONDS);
            
            if (response.isSuccess()) {
                log.info("포인트 예약 취소 성공: customerId={}, orderId={}", customerId, orderId);
            } else {
                log.error("포인트 예약 취소 실패: customerId={}, orderId={}, error={}", 
                        customerId, orderId, response.getErrorMessage());
            }
        } catch (TimeoutException e) {
            log.warn("포인트 예약 취소 타임아웃: customerId={}, orderId={}", customerId, orderId);
        } catch (Exception e) {
            log.error("포인트 예약 취소 실패: customerId={}, orderId={}", customerId, orderId);
        }
    }

    private String createPaymentRequestAsync(UUID orderId, UUID customerId, Integer amount, String sagaId) {
        try {
            PaymentRequestEvent requestEvent = PaymentRequestEvent.builder()
                    .orderId(orderId)
                    .customerId(customerId)
                    .amount(amount)
                    .sagaId(sagaId)
                    .build();
            
            kafkaTemplate.send("payment.request", orderId.toString(), requestEvent);
            log.info("결제 요청 이벤트 발행: orderId={}, customerId={}, amount={}, sagaId={}", 
                    orderId, customerId, amount, sagaId);

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
        } catch (TimeoutException e) {
            log.warn("결제 요청 타임아웃: orderId={}, customerId={}, amount={}", 
                    orderId, customerId, amount);
            throw new RuntimeException("결제 요청이 시간 초과되었습니다");
        } catch (Exception e) {
            log.error("결제 요청 실패: orderId={}, customerId={}, amount={}", 
                     orderId, customerId, amount);
            throw new RuntimeException("결제 요청에 실패했습니다: " + e.getMessage());
        }
    }

    private void cancelPaymentRequestAsync(UUID orderId, String sagaId) {
        try {
            PaymentRequestCancelEvent cancelEvent = PaymentRequestCancelEvent.builder()
                    .orderId(orderId)
                    .sagaId(sagaId)
                    .build();
            
            kafkaTemplate.send("payment.request.cancel", orderId.toString(), cancelEvent);
            log.info("결제 요청 취소 이벤트 발행: orderId={}, sagaId={}", orderId, sagaId);

            CompletableFuture<PaymentRequestCancelResponseEvent> responseFuture = 
                    paymentRequestCancelResponseEventConsumer.waitForResponse(sagaId);
            
            PaymentRequestCancelResponseEvent response = responseFuture.get(5, TimeUnit.SECONDS);
            
            if (response.isSuccess()) {
                log.info("결제 요청 취소 성공: orderId={}", orderId);
            } else {
                log.error("결제 요청 취소 실패: orderId={}, error={}", orderId, response.getErrorMessage());
            }
        } catch (TimeoutException e) {
            log.warn("결제 요청 취소 타임아웃: orderId={}", orderId);
        } catch (Exception e) {
            log.error("결제 요청 취소 실패: orderId={}", orderId);
        }
    }

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
