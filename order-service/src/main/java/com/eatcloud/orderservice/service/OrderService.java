package com.eatcloud.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eatcloud.orderservice.entity.Order;
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
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusCodeRepository orderStatusCodeRepository;
    private final OrderTypeCodeRepository orderTypeCodeRepository;
    private final ExternalApiService externalApiService;
    private final DistributedLockService distributedLockService;
    private final OrderEventProducer orderEventProducer;
    @Lazy

    @Autowired
    private CartService cartService;

    public CreateOrderResponse createOrderFromCartSimple(UUID customerId, CreateOrderRequest request, String bearerToken) {
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
                    if (cartItems.isEmpty()) {
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

                    Order order = createPendingOrder(
                        customerId,
                        request.getStoreId(),
                        orderMenuList,
                        request.getOrderType(),
                        request.getUsePoints(),
                        request.getPointsToUse(),
                        bearerToken
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
                        orderEventProducer.publishOrderCreated(event);
                    } catch (Exception publishEx) {
                        log.error("주문 생성 이벤트 발행 실패: orderId={}", order.getOrderId(), publishEx);
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
                                   Boolean usePoints, Integer pointsToUse, String bearerToken) {
        String orderNumber = generateOrderNumber();

        OrderStatusCode statusCode = orderStatusCodeRepository.findByCode("PENDING")
                .orElseThrow(() -> new RuntimeException("주문 상태 코드를 찾을 수 없습니다: PENDING"));

        OrderTypeCode typeCode = orderTypeCodeRepository.findByCode(orderType)
                .orElseThrow(() -> new RuntimeException("주문 타입 코드를 찾을 수 없습니다: " + orderType));

        for (OrderMenu orderMenu : orderMenuList) {
            try {
                Integer menuPrice = externalApiService.getMenuPrice(orderMenu.getMenuId());
                if (menuPrice != null && menuPrice > 0) {
                    orderMenu.setPrice(menuPrice);
                } else {
                    log.warn("Menu price is null/invalid for menuId: {}, keep cart price: {}",
                            orderMenu.getMenuId(), orderMenu.getPrice());
                }
            } catch (Exception e) {
                log.warn("Store-service unavailable. Fallback to cart price for menuId: {}. reason={}",
                        orderMenu.getMenuId(), e.getMessage());
            }
        }

        Integer totalPrice = calculateTotalAmount(orderMenuList);

        if (usePoints == null) {
            usePoints = false;
        }
        if (pointsToUse == null) {
            pointsToUse = 0;
        }

        if (usePoints && pointsToUse > 0) {
            try {
                Integer customerPoints = externalApiService.getCustomerPoints(customerId, bearerToken);
                
                if (customerPoints == null) {
                    throw new RuntimeException("사용자 포인트를 조회할 수 없습니다. 잠시 후 다시 시도해주세요.");
                }
                
                if (pointsToUse > customerPoints) {
                    throw new RuntimeException(
                        String.format("포인트가 부족합니다. 보유 포인트: %d원, 사용하려는 포인트: %d원", 
                                    customerPoints, pointsToUse)
                    );
                }
                
                if (pointsToUse > totalPrice) {
                    throw new RuntimeException(
                        String.format("포인트는 주문 총액을 초과할 수 없습니다. 주문 총액: %d원, 사용하려는 포인트: %d원", 
                                    totalPrice, pointsToUse)
                    );
                }
                
                log.info("포인트 사용 검증 통과: customerId={}, 보유포인트={}, 사용포인트={}, 주문총액={}", 
                         customerId, customerPoints, pointsToUse, totalPrice);
                         
            } catch (Exception e) {
                log.error("포인트 검증 실패: customerId={}, pointsToUse={}", customerId, pointsToUse, e);
                throw new RuntimeException("포인트 검증에 실패했습니다: " + e.getMessage());
            }
        }

        Integer finalPaymentAmount = Math.max(totalPrice - pointsToUse, 0);

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

        return orderRepository.save(order);
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
        orderRepository.save(order);

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

        OrderStatusCode canceledStatus = orderStatusCodeRepository.findByCode("CANCELED")
                .orElseThrow(() -> new RuntimeException("주문 상태 코드를 찾을 수 없습니다: CANCELED"));
        order.setOrderStatusCode(canceledStatus);

        orderRepository.save(order);

        // 주문 취소 이벤트 발행
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
            orderEventProducer.publishOrderCancelled(event);
            log.info("주문 취소 이벤트 발행 완료: orderId={}, customerId={}", orderId, order.getCustomerId());
        } catch (Exception publishEx) {
            log.error("주문 취소 이벤트 발행 실패: orderId={}", orderId, publishEx);
        }

        log.info("주문 취소 처리 완료: orderId={}, reason={}", orderId, cancelReason);
    }

    /**
     * 주문 조회 (ID로)
     */
    @Transactional(readOnly = true)
    public Order getOrderById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderId));
    }

    /**
     * PENDING 상태의 주문 생성 (Saga용)
     */
    @Transactional
    public Order createPendingOrder(UUID customerId, UUID storeId, List<OrderMenu> orderMenuList, 
                                   String orderType, Boolean usePoints, Integer pointsToUse) {
        
        log.info("Creating pending order: customerId={}, storeId={}, orderType={}", 
                customerId, storeId, orderType);

        // 주문 총액 계산
        Integer totalAmount = calculateTotalAmount(orderMenuList);
        Integer finalAmount = totalAmount;
        Integer actualPointsToUse = 0;

        // 포인트 사용 계산
        if (Boolean.TRUE.equals(usePoints) && pointsToUse != null && pointsToUse > 0) {
            actualPointsToUse = Math.min(pointsToUse, totalAmount);
            finalAmount = totalAmount - actualPointsToUse;
        }

        // OrderType과 OrderStatus 조회
        OrderTypeCode orderTypeCode = orderTypeCodeRepository.findByCode(orderType)
                .orElseThrow(() -> new RuntimeException("주문 타입을 찾을 수 없습니다: " + orderType));
        
        OrderStatusCode pendingStatus = orderStatusCodeRepository.findByCode("PENDING")
                .orElseThrow(() -> new RuntimeException("주문 상태 코드를 찾을 수 없습니다: PENDING"));

        // 주문 생성
        Order order = Order.builder()
                .customerId(customerId)
                .storeId(storeId)
                .orderNumber(generateOrderNumber())
                .totalPrice(totalAmount)
                .finalPaymentAmount(finalAmount)
                .pointsToUse(actualPointsToUse)
                .orderTypeCode(orderTypeCode)
                .orderStatusCode(pendingStatus)
                .build();

        Order savedOrder = orderRepository.save(order);

        // 주문 아이템 저장
        for (OrderMenu menu : orderMenuList) {
            com.eatcloud.orderservice.entity.OrderItem orderItem = 
                    com.eatcloud.orderservice.entity.OrderItem.builder()
                    .order(savedOrder)
                    .menuId(menu.getMenuId())
                    .menuName(menu.getMenuName())
                    .quantity(menu.getQuantity())
                    .unitPrice(menu.getPrice())
                    .totalPrice(menu.getPrice() * menu.getQuantity())
                    .build();
            
            orderItemRepository.save(orderItem);
        }

        log.info("Pending order created: orderId={}, orderNumber={}, totalAmount={}, finalAmount={}", 
                savedOrder.getOrderId(), savedOrder.getOrderNumber(), totalAmount, finalAmount);

        return savedOrder;
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
    }
}
