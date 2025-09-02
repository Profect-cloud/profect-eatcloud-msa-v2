// package com.eatcloud.orderservice.service;
//
// import com.eatcloud.orderservice.dto.CartItem;
// import com.eatcloud.orderservice.dto.OrderMenu;
// import com.eatcloud.orderservice.dto.request.CreateOrderRequest;
// import com.eatcloud.orderservice.dto.response.CreateOrderResponse;
// import com.eatcloud.orderservice.entity.Order;
// import com.eatcloud.orderservice.exception.ErrorCode;
// import com.eatcloud.orderservice.exception.OrderException;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
//
// import org.springframework.context.annotation.Lazy;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;
//
// import java.util.List;
// import java.util.UUID;
// import java.util.concurrent.TimeUnit;
// import java.util.stream.Collectors;
//
// /**
//  * Saga 패턴 오케스트레이터
//  * 분산 트랜잭션을 관리하고 보상 로직을 처리
//  */
// @Service
// @RequiredArgsConstructor
// @Slf4j
// public class SagaOrchestrator {
//
//     private final DistributedLockService lockService;
//     @Lazy
//     // private final OrderService orderService;
//     private final CartService cartService;
//     private final ExternalApiService externalApiService;
//
//     /**
//      * 주문 생성 Saga - 분산 트랜잭션 처리
//      * 1. 분산락 획득
//      * 2. 재고 확인 및 예약
//      * 3. 포인트 차감 (선택적)
//      * 4. 주문 생성
//      * 5. 결제 준비
//      * 6. 장바구니 비우기
//      */
//     @Transactional
//     public CreateOrderResponse createOrderSaga(UUID customerId, CreateOrderRequest request) {
//         String sagaId = UUID.randomUUID().toString();
//         SagaTransaction saga = new SagaTransaction(sagaId);
//
//         log.info("Starting order saga: sagaId={}, customerId={}, storeId={}",
//                 sagaId, customerId, request.getStoreId());
//
//         try {
//             // 분산락 키 설정 (고객, 매장, 장바구니에 대한 락)
//             String[] lockKeys = {
//                 "customer:" + customerId,
//                 "store:" + request.getStoreId(),
//                 "cart:" + customerId
//             };
//
//             // 분산락을 사용한 트랜잭션 실행
//             return lockService.executeWithMultiLock(
//                 lockKeys,
//                 10,  // 10초 대기
//                 30,  // 30초 유지
//                 TimeUnit.SECONDS,
//                 () -> executeOrderCreation(customerId, request, saga)
//             );
//
//         } catch (Exception e) {
//             log.error("Order saga failed: sagaId={}, error={}", sagaId, e.getMessage(), e);
//
//             // Saga 보상 실행
//             saga.compensate();
//
//             if (e instanceof OrderException) {
//                 throw (OrderException) e;
//             }
//             throw new OrderException(ErrorCode.ORDER_PROCESSING,
//                     "주문 처리 중 오류가 발생했습니다: " + e.getMessage());
//         }
//     }
//
//     /**
//      * 실제 주문 생성 로직 실행
//      */
//     private CreateOrderResponse executeOrderCreation(UUID customerId, CreateOrderRequest request,
//                                                      SagaTransaction saga) throws Exception {
//         log.info("Executing order creation: customerId={}, storeId={}", customerId, request.getStoreId());
//
//         // Step 1: 장바구니 조회
//         log.info("Step 1: Fetching cart items for customer: {}", customerId);
//         List<CartItem> cartItems = cartService.getCart(customerId);
//         if (cartItems.isEmpty()) {
//             throw new OrderException(ErrorCode.EMPTY_CART, "장바구니가 비어있습니다.");
//         }
//
//         // Step 2: 재고 확인 (store-service 호출)
//         log.info("Step 2: Checking inventory availability");
//         boolean inventoryAvailable = checkInventoryAvailability(request.getStoreId(), cartItems);
//         if (!inventoryAvailable) {
//             throw new OrderException(ErrorCode.INSUFFICIENT_INVENTORY, "재고가 부족합니다.");
//         }
//
//         // Step 3: 장바구니 아이템을 주문 메뉴로 변환
//         log.info("Step 3: Converting cart items to order menu");
//         List<OrderMenu> orderMenuList = cartItems.stream()
//             .map(item -> OrderMenu.builder()
//                 .menuId(item.getMenuId())
//                 .menuName(item.getMenuName())
//                 .quantity(item.getQuantity())
//                 .price(item.getPrice())
//                 .build())
//             .collect(Collectors.toList());
//
//         // Step 4: 메뉴 가격 검증 및 업데이트
//         log.info("Step 4: Validating menu prices");
//         validateAndUpdateMenuPrices(orderMenuList);
//
//         // Step 5: 포인트 차감 (사용하는 경우)
//         String pointTransactionId = null;
//         if (Boolean.TRUE.equals(request.getUsePoints()) && request.getPointsToUse() > 0) {
//             log.info("Step 5: Deducting points: {} points", request.getPointsToUse());
//             pointTransactionId = deductCustomerPoints(customerId, request.getPointsToUse());
//
//             // 포인트 복구를 위한 보상 추가
//             final String txId = pointTransactionId;
//             saga.addCompensation("Refund Points",
//                 () -> refundCustomerPoints(customerId, txId, request.getPointsToUse()));
//         }
//
//         // Step 6: 주문 생성
//         log.info("Step 6: Creating pending order");
//         Order order = orderService.createPendingOrder(
//             customerId,
//             request.getStoreId(),
//             orderMenuList,
//             request.getOrderType(),
//             request.getUsePoints(),
//             request.getPointsToUse()
//         );
//
//         // 주문 취소를 위한 보상 추가
//         saga.addCompensation("Cancel Order",
//             () -> orderService.cancelOrder(order.getOrderId()));
//
//         // Step 7: 재고 예약
//         log.info("Step 7: Reserving inventory for order: {}", order.getOrderNumber());
//         String reservationId = reserveInventory(request.getStoreId(), orderMenuList);
//
//         // 재고 예약 취소를 위한 보상 추가
//         saga.addCompensation("Release Inventory",
//             () -> releaseInventory(request.getStoreId(), reservationId));
//
//         // Step 8: 장바구니 비우기
//         log.info("Step 8: Clearing cart for customer: {}", customerId);
//         try {
//             cartService.clearCart(customerId);
//         } catch (Exception e) {
//             // 장바구니 삭제 실패는 critical하지 않음
//             log.warn("Failed to clear cart for customer: {}, continuing...", customerId, e);
//         }
//
//         // Saga 완료
//         saga.complete();
//
//         log.info("Order created successfully: orderId={}, orderNumber={}",
//                 order.getOrderId(), order.getOrderNumber());
//
//         // 응답 생성
//         return CreateOrderResponse.builder()
//             .orderId(order.getOrderId())
//             .orderNumber(order.getOrderNumber())
//             .totalPrice(order.getTotalPrice())
//             .finalPaymentAmount(order.getFinalPaymentAmount())
//             .orderStatus(order.getOrderStatusCode().getCode())
//             .message("주문이 성공적으로 생성되었습니다. 결제를 진행해주세요.")
//             .build();
//     }
//
//     /**
//      * 재고 가용성 확인
//      */
//     private boolean checkInventoryAvailability(UUID storeId, List<CartItem> items) {
//         try {
//             // TODO: store-service API 호출 구현
//             // 현재는 항상 true 반환 (개발 중)
//             return true;
//         } catch (Exception e) {
//             log.error("Failed to check inventory availability: {}", e.getMessage());
//             return false;
//         }
//     }
//
//     /**
//      * 메뉴 가격 검증 및 업데이트
//      */
//     private void validateAndUpdateMenuPrices(List<OrderMenu> orderMenuList) {
//         for (OrderMenu menu : orderMenuList) {
//             try {
//                 Integer currentPrice = externalApiService.getMenuPrice(menu.getMenuId());
//                 menu.setPrice(currentPrice);
//             } catch (Exception e) {
//                 log.error("Failed to get menu price for menuId: {}", menu.getMenuId(), e);
//                 throw new OrderException(ErrorCode.MENU_NOT_FOUND,
//                     "메뉴 가격을 조회할 수 없습니다: " + menu.getMenuId());
//             }
//         }
//     }
//
//     /**
//      * 고객 포인트 차감
//      */
//     private String deductCustomerPoints(UUID customerId, Integer points) {
//         try {
//             // TODO: customer-service API 호출 구현
//             // 트랜잭션 ID 반환
//             return UUID.randomUUID().toString();
//         } catch (Exception e) {
//             log.error("Failed to deduct customer points: {}", e.getMessage());
//             throw new OrderException(ErrorCode.POINT_DEDUCTION_FAILED,
//                 "포인트 차감에 실패했습니다.");
//         }
//     }
//
//     /**
//      * 고객 포인트 환불 (보상)
//      */
//     private void refundCustomerPoints(UUID customerId, String transactionId, Integer points) {
//         try {
//             log.info("Refunding {} points to customer: {}, transactionId: {}",
//                     points, customerId, transactionId);
//             // TODO: customer-service API 호출 구현
//         } catch (Exception e) {
//             log.error("Failed to refund customer points: customerId={}, transactionId={}, points={}",
//                      customerId, transactionId, points, e);
//         }
//     }
//
//     /**
//      * 재고 예약
//      */
//     private String reserveInventory(UUID storeId, List<OrderMenu> items) {
//         try {
//             // TODO: store-service API 호출 구현
//             // 예약 ID 반환
//             return UUID.randomUUID().toString();
//         } catch (Exception e) {
//             log.error("Failed to reserve inventory: {}", e.getMessage());
//             throw new OrderException(ErrorCode.INVENTORY_RESERVATION_FAILED,
//                 "재고 예약에 실패했습니다.");
//         }
//     }
//
//     /**
//      * 재고 예약 취소 (보상)
//      */
//     private void releaseInventory(UUID storeId, String reservationId) {
//         try {
//             log.info("Releasing inventory reservation: storeId={}, reservationId={}",
//                     storeId, reservationId);
//             // TODO: store-service API 호출 구현
//         } catch (Exception e) {
//             log.error("Failed to release inventory: storeId={}, reservationId={}",
//                      storeId, reservationId, e);
//         }
//     }
// }
