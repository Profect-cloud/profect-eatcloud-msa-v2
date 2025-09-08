package com.eatcloud.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eatcloud.logging.annotation.Loggable;
import com.eatcloud.orderservice.entity.Order;
import com.eatcloud.orderservice.kafka.producer.OrderEventProducer;
import com.eatcloud.orderservice.repository.OrderItemRepository;
import com.eatcloud.orderservice.repository.OrderRepository;
import com.eatcloud.orderservice.dto.OrderMenu;
import com.eatcloud.orderservice.entity.OrderStatusCode;
import com.eatcloud.orderservice.entity.OrderTypeCode;
import com.eatcloud.orderservice.repository.OrderStatusCodeRepository;
import com.eatcloud.orderservice.repository.OrderTypeCodeRepository;
import com.eatcloud.orderservice.exception.OrderException;
import com.eatcloud.orderservice.exception.ErrorCode;
import org.springframework.context.annotation.Lazy;

import com.eatcloud.orderservice.dto.CartItem;
import com.eatcloud.orderservice.dto.request.CreateOrderRequest;
import com.eatcloud.orderservice.dto.response.CreateOrderResponse;
import com.eatcloud.orderservice.event.PaymentCompletedEvent;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true, maskSensitiveData = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusCodeRepository orderStatusCodeRepository;
    private final OrderTypeCodeRepository orderTypeCodeRepository;
    private final DistributedLockService distributedLockService;
    private final OutboxService outboxService;
    @Lazy

    @Autowired
    private CartService cartService;

    public CreateOrderResponse createOrderFromCartSimple(UUID customerId, CreateOrderRequest request, String bearerToken) {
        log.info("=== createOrderFromCartSimple called ===");
        log.info("customerId: {}, storeId: {}, bearerToken: {}", customerId, request.getStoreId(), bearerToken != null ? "present" : "null");
        
        String lockKey = "order:create:" + customerId;
        
        try {
            return distributedLockService.executeWithLock(
                lockKey,
                5,
                10,
                TimeUnit.SECONDS,
                () -> {
                    log.info("Starting order creation for customer: {}", customerId);

                    List<CartItem> cartItems = cartService.getCart(customerId);
                    log.info("Cart items retrieved: count={}, items={}", cartItems.size(), cartItems);
                    if (cartItems.isEmpty()) {
                        log.warn("Cart is empty for customer: {}", customerId);
                        throw new OrderException(ErrorCode.EMPTY_CART);
                    }

                    List<OrderMenu> orderMenuList = cartItems.stream()
                        .map(item -> OrderMenu.builder()
                            .menuId(item.getMenuId())
                            .menuName(item.getMenuName())
                            .quantity(item.getQuantity())
                            .price(item.getPrice())
                            .build())
                        .collect(Collectors.toList());
                    
                    log.info("OrderMenuList created: size={}, items={}", orderMenuList.size(), orderMenuList);

                    Order order = createPendingOrder(
                        customerId,
                        request.getStoreId(),
                        orderMenuList,
                        request.getOrderType(),
                        request.getUsePoints(),
                        request.getPointsToUse()
                    );

                    try {
                        com.eatcloud.orderservice.event.OrderCreatedEvent event =
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
                                event,
                                outboxService.defaultHeaders(null, null)
                        );
                    } catch (Exception publishEx) {
                        log.error("주문 생성 Outbox 기록 실패: orderId={}", order.getOrderId(), publishEx);
                    }

                    try {
                        cartService.clearCart(customerId);
                        log.info("Cart cleared for customer: {}", customerId);
                    } catch (Exception e) {
                        log.error("Failed to clear cart for customer: {}, but order created successfully", customerId, e);
                    }

                    log.info("Order created successfully: orderId={}, customerId={}", order.getOrderId(), customerId);

                    return CreateOrderResponse.builder()
                        .orderId(order.getOrderId())
                        .orderNumber(order.getOrderNumber())
                        .totalPrice(order.getTotalPrice())
                        .finalPaymentAmount(order.getFinalPaymentAmount())
                        .orderStatus(order.getOrderStatusCode().getCode())
                        .message("주문이 생성되었습니다.")
                        .build();
                }
            );
        } catch (Exception e) {
            log.error("Order creation failed for customer: {}", customerId, e);
            log.error("Exception type: {}, message: {}", e.getClass().getSimpleName(), e.getMessage());

            if (e.getMessage() != null && (
                e.getMessage().contains("포인트가 부족합니다") ||
                e.getMessage().contains("포인트는 주문 총액을 초과할 수 없습니다") ||
                e.getMessage().contains("포인트 검증에 실패했습니다") ||
                e.getMessage().contains("사용자 포인트를 조회할 수 없습니다") ||
                e.getMessage().contains("Customer service is temporarily unavailable")
            )) {
                throw new RuntimeException(e.getMessage(), e);
            }
            
            if (e instanceof OrderException) {
                throw (OrderException) e;
            }
            throw new OrderException(ErrorCode.ORDER_PROCESSING);
        }
    }

    public Order createPendingOrder(UUID customerId, UUID storeId, List<OrderMenu> orderMenuList, String orderType,
                                   Boolean usePoints, Integer pointsToUse) {
        log.info("=== createPendingOrder called ===");
        log.info("customerId: {}, storeId: {}, orderMenuList size: {}", customerId, storeId, orderMenuList != null ? orderMenuList.size() : "null");
        log.info("orderMenuList: {}", orderMenuList);
        
        String orderNumber = generateOrderNumber();

        OrderStatusCode statusCode = orderStatusCodeRepository.findByCode("PENDING")
                .orElseThrow(() -> new RuntimeException("주문 상태 코드를 찾을 수 없습니다: PENDING"));

        OrderTypeCode typeCode = orderTypeCodeRepository.findByCode(orderType)
                .orElseThrow(() -> new RuntimeException("주문 타입 코드를 찾을 수 없습니다: " + orderType));

        log.info("Using cart prices for order creation. Menu count: {}", orderMenuList.size());

        Integer totalPrice = calculateTotalAmount(orderMenuList);

        if (usePoints == null) {
            usePoints = false;
        }
        if (pointsToUse == null) {
            pointsToUse = 0;
        }

        if (usePoints && pointsToUse > 0) {
            log.info("포인트 사용 예정: customerId={}, pointsToUse={}, totalPrice={}", 
                    customerId, pointsToUse, totalPrice);

            if (pointsToUse > totalPrice) {
                throw new RuntimeException(
                    String.format("포인트는 주문 총액을 초과할 수 없습니다. 주문 총액: %d원, 사용하려는 포인트: %d원", 
                                totalPrice, pointsToUse)
                );
            }
        }

        Integer finalPaymentAmount = Math.max(totalPrice - pointsToUse, 0);

        if (orderMenuList == null) {
            orderMenuList = new ArrayList<>();
            log.warn("orderMenuList was null, initializing with empty list");
        }

        log.info("Creating Order with orderMenuList: size={}, items={}", 
                orderMenuList.size(), orderMenuList);

        if (orderMenuList.isEmpty()) {
            log.warn("orderMenuList is empty! This will cause database constraint violation.");
        }

        if (orderMenuList == null) {
            log.error("orderMenuList is null! This should not happen.");
            throw new RuntimeException("orderMenuList cannot be null");
        }

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .orderMenuList(orderMenuList)
                .customerId(customerId)
                .storeId(storeId)
                .orderStatusCode(statusCode)
                .orderTypeCode(typeCode)
                .totalPrice(totalPrice)
                .usePoints(usePoints)
                .pointsToUse(pointsToUse)
                .finalPaymentAmount(finalPaymentAmount)
                .build();

        log.info("Order entity created: orderId={}, orderMenuList size={}", order.getOrderId(), order.getOrderMenuList() != null ? order.getOrderMenuList().size() : "null");
        log.info("Order entity orderMenuList: {}", order.getOrderMenuList());

        Order savedOrder = orderRepository.save(order);
        log.info("Order saved to database: orderId={}, orderMenuList size={}", savedOrder.getOrderId(), savedOrder.getOrderMenuList() != null ? savedOrder.getOrderMenuList().size() : "null");
        
        return savedOrder;
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID orderId) {
        return orderRepository.findById(orderId);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber);
    }

    public void completePayment(UUID orderId, UUID paymentId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderId));

        if ("PAID".equals(order.getOrderStatusCode().getCode())) {
            log.warn("주문이 이미 결제 완료 상태입니다: orderId={}, paymentId={}", orderId, paymentId);
            return;
        }

        if (!"PENDING".equals(order.getOrderStatusCode().getCode())) {
            log.error("결제 완료할 수 없는 주문 상태: orderId={}, currentStatus={}",
                     orderId, order.getOrderStatusCode().getCode());
            throw new RuntimeException("결제 완료할 수 없는 주문 상태입니다: " + order.getOrderStatusCode().getCode());
        }

        OrderStatusCode paidStatus = orderStatusCodeRepository.findByCode("PAID")
                .orElseThrow(() -> new RuntimeException("주문 상태 코드를 찾을 수 없습니다: PAID"));

        order.setPaymentId(paymentId);
        order.setOrderStatusCode(paidStatus);

        log.info("주문 결제 완료 처리: orderId={}, paymentId={}", orderId, paymentId);
    }

    public void failPayment(UUID orderId, String failureReason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderId));

        if (!"PENDING".equals(order.getOrderStatusCode().getCode())) {
            log.warn("결제 실패 처리할 수 없는 주문 상태: orderId={}, currentStatus={}",
                    orderId, order.getOrderStatusCode().getCode());
            return;
        }

        OrderStatusCode failedStatus = orderStatusCodeRepository.findByCode("PAYMENT_FAILED")
                .orElseThrow(() -> new RuntimeException("주문 상태 코드를 찾을 수 없습니다: PAYMENT_FAILED"));

        order.setOrderStatusCode(failedStatus);
        orderRepository.save(order);

        log.info("주문 결제 실패 처리: orderId={}, reason={}", orderId, failureReason);
    }

    public void cancelOrder(UUID orderId) {
        cancelOrder(orderId, "고객 요청에 의한 취소");
    }

    public void cancelOrder(UUID orderId, String cancelReason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderId));

        OrderStatusCode canceledStatus = orderStatusCodeRepository.findByCode("CANCELLED")
                .orElseThrow(() -> new RuntimeException("주문 상태 코드를 찾을 수 없습니다: CANCELLED"));
        order.setOrderStatusCode(canceledStatus);

        orderRepository.save(order);

        try {
            com.eatcloud.orderservice.event.OrderCancelledEvent event =
                    com.eatcloud.orderservice.event.OrderCancelledEvent.builder()
                            .orderId(order.getOrderId())
                            .customerId(order.getCustomerId())
                            .storeId(order.getStoreId())
                            .cancelReason(cancelReason)
                            .cancelledAt(java.time.LocalDateTime.now())
                            .createdAt(java.time.LocalDateTime.now())
                            .build();

            outboxService.saveEvent(
                    "Order",
                    order.getOrderId().toString(),
                    "OrderCancelledEvent",
                    event,
                    outboxService.defaultHeaders(null, null)
            );
            log.info("주문 취소 Outbox 기록 완료: orderId={}, customerId={}", orderId, order.getCustomerId());
        } catch (Exception publishEx) {
            log.error("주문 취소 Outbox 기록 실패: orderId={}", orderId, publishEx);
        }

        log.info("주문 취소 처리 완료: orderId={}, reason={}", orderId, cancelReason);
    }

    @Transactional(readOnly = true)
    public Integer calculateTotalAmount(List<OrderMenu> orderMenuList) {
        return orderMenuList.stream()
                .mapToInt(menu -> menu.getPrice() * menu.getQuantity())
                .sum();
    }

    private String generateOrderNumber() {
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        return "ORD-" + date + "-" + randomPart;
    }

    public List<Order> findOrdersByCustomer(UUID customerId) {
        return orderRepository.findAllByCustomerId(customerId);
    }

    public Order findOrderByCustomerAndOrderId(UUID customerId, UUID orderId) {
        		return orderRepository.findByOrderIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId)
                .orElseThrow(() -> new RuntimeException("해당 주문이 없습니다."));
    }

    public List<Order> findOrdersByStore(UUID storeId) {
        return orderRepository.findAllByStoreId(storeId);
    }

    public Order findOrderByStoreAndOrderId(UUID storeId, UUID orderId) {
        return orderRepository.findByOrderIdAndStoreId(orderId, storeId)
                .orElseThrow(() -> new RuntimeException("해당 매장에 주문이 없습니다."));
    }

    @Transactional
    public void updateOrderStatus(UUID orderId, String statusCode) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));

        OrderStatusCode statusCodeEntity = orderStatusCodeRepository.findById(statusCode)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 상태 코드입니다."));

        order.setOrderStatusCode(statusCodeEntity);
        orderRepository.save(order);

        log.info("주문 상태 업데이트 완료: orderId={}, newStatus={}", orderId, statusCode);
    }
}
