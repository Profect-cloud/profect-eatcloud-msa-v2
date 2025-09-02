package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.dto.response.AdminOrderResponseDto;
import com.eatcloud.orderservice.entity.Order;
import com.eatcloud.orderservice.entity.OrderStatusCode;
import com.eatcloud.orderservice.repository.OrderRepository;
import com.eatcloud.orderservice.repository.OrderStatusCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminOrderService 단위 테스트")
class AdminOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusCodeRepository orderStatusCodeRepository;

    @InjectMocks
    private AdminOrderService adminOrderService;

    private UUID orderId;
    private Order order;
    private OrderStatusCode paidStatus;
    private OrderStatusCode confirmedStatus;
    private OrderStatusCode completedStatus;
    private OrderStatusCode pendingStatus;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();

        paidStatus = OrderStatusCode.builder()
                .code("PAID")
                .displayName("결제완료")
                .build();

        confirmedStatus = OrderStatusCode.builder()
                .code("CONFIRMED")
                .displayName("주문확인")
                .build();

        completedStatus = OrderStatusCode.builder()
                .code("COMPLETED")
                .displayName("완료")
                .build();

        pendingStatus = OrderStatusCode.builder()
                .code("PENDING")
                .displayName("대기중")
                .build();

        order = Order.builder()
                .orderId(orderId)
                .orderNumber("ORD-20241215-ABCDE")
                .orderStatusCode(paidStatus)
                .build();
    }

    @Test
    @DisplayName("주문 수락 - 성공")
    void confirmOrder_Success() {
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderStatusCodeRepository.findByCode("CONFIRMED")).willReturn(Optional.of(confirmedStatus));
        given(orderRepository.save(any(Order.class))).willReturn(order);

        AdminOrderResponseDto response = adminOrderService.confirmOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(orderId);
        assertThat(response.getOrderNumber()).isEqualTo("ORD-20241215-ABCDE");
        assertThat(response.getOrderStatus()).isEqualTo("CONFIRMED");
        assertThat(response.getMessage()).isEqualTo("주문이 수락되었습니다.");

        verify(orderRepository).findById(orderId);
        verify(orderStatusCodeRepository).findByCode("CONFIRMED");
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 수락 - 주문 없음 예외")
    void confirmOrder_OrderNotFound_ThrowsException() {
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminOrderService.confirmOrder(orderId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("주문을 찾을 수 없습니다: " + orderId);

        verify(orderRepository).findById(orderId);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 수락 - 결제 미완료 상태 예외")
    void confirmOrder_NotPaidStatus_ThrowsException() {
        Order pendingOrder = Order.builder()
                .orderId(orderId)
                .orderNumber("ORD-20241215-ABCDE")
                .orderStatusCode(pendingStatus)
                .build();

        given(orderRepository.findById(orderId)).willReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() -> adminOrderService.confirmOrder(orderId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("결제 완료된 주문만 수락할 수 있습니다. 현재 상태: PENDING");

        verify(orderRepository).findById(orderId);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 수락 - 상태 코드 없음 예외")
    void confirmOrder_StatusCodeNotFound_ThrowsException() {
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderStatusCodeRepository.findByCode("CONFIRMED")).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminOrderService.confirmOrder(orderId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("주문 상태 코드를 찾을 수 없습니다: CONFIRMED");

        verify(orderRepository).findById(orderId);
        verify(orderStatusCodeRepository).findByCode("CONFIRMED");
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 완료 - 성공")
    void completeOrder_Success() {
        Order confirmedOrder = Order.builder()
                .orderId(orderId)
                .orderNumber("ORD-20241215-ABCDE")
                .orderStatusCode(confirmedStatus)
                .build();

        given(orderRepository.findById(orderId)).willReturn(Optional.of(confirmedOrder));
        given(orderStatusCodeRepository.findByCode("COMPLETED")).willReturn(Optional.of(completedStatus));
        given(orderRepository.save(any(Order.class))).willReturn(confirmedOrder);


        AdminOrderResponseDto response = adminOrderService.completeOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(orderId);
        assertThat(response.getOrderNumber()).isEqualTo("ORD-20241215-ABCDE");
        assertThat(response.getOrderStatus()).isEqualTo("COMPLETED");
        assertThat(response.getMessage()).isEqualTo("주문이 완료되었습니다.");

        verify(orderRepository).findById(orderId);
        verify(orderStatusCodeRepository).findByCode("COMPLETED");
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 완료 - 수락되지 않은 주문 예외")
    void completeOrder_NotConfirmedStatus_ThrowsException() {
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order)); // PAID 상태

        assertThatThrownBy(() -> adminOrderService.completeOrder(orderId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("수락된 주문만 완료할 수 있습니다. 현재 상태: PAID");

        verify(orderRepository).findById(orderId);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 상태 조회 - 성공")
    void getOrderStatus_Success() {
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        AdminOrderResponseDto response = adminOrderService.getOrderStatus(orderId);
        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(orderId);
        assertThat(response.getOrderNumber()).isEqualTo("ORD-20241215-ABCDE");
        assertThat(response.getOrderStatus()).isEqualTo("PAID");
        assertThat(response.getMessage()).isEqualTo("주문 상태 조회 완료");

        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("주문 상태 조회 - 주문 없음 예외")
    void getOrderStatus_OrderNotFound_ThrowsException() {
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminOrderService.getOrderStatus(orderId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("주문을 찾을 수 없습니다: " + orderId);

        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("주문 상태별 비즈니스 로직 테스트 - 전체 플로우")
    void orderStatusFlow_CompleteWorkflow() {
        Order paidOrder = Order.builder()
                .orderId(orderId)
                .orderNumber("ORD-20241215-ABCDE")
                .orderStatusCode(paidStatus)
                .build();

        given(orderRepository.findById(orderId)).willReturn(Optional.of(paidOrder));
        given(orderStatusCodeRepository.findByCode("CONFIRMED")).willReturn(Optional.of(confirmedStatus));

        Order confirmedOrder = Order.builder()
                .orderId(orderId)
                .orderNumber("ORD-20241215-ABCDE")
                .orderStatusCode(confirmedStatus)
                .build();
        given(orderRepository.save(any(Order.class))).willReturn(confirmedOrder);

        AdminOrderResponseDto confirmResponse = adminOrderService.confirmOrder(orderId);

        assertThat(confirmResponse.getOrderStatus()).isEqualTo("CONFIRMED");

        given(orderRepository.findById(orderId)).willReturn(Optional.of(confirmedOrder));
        given(orderStatusCodeRepository.findByCode("COMPLETED")).willReturn(Optional.of(completedStatus));

        Order completedOrder = Order.builder()
                .orderId(orderId)
                .orderNumber("ORD-20241215-ABCDE")
                .orderStatusCode(completedStatus)
                .build();
        given(orderRepository.save(any(Order.class))).willReturn(completedOrder);

        AdminOrderResponseDto completeResponse = adminOrderService.completeOrder(orderId);

        assertThat(completeResponse.getOrderStatus()).isEqualTo("COMPLETED");

        verify(orderRepository, times(2)).findById(orderId);
        verify(orderRepository, times(2)).save(any(Order.class));
    }

    @Test
    @DisplayName("잘못된 상태 전환 시도 - 비즈니스 규칙 위반")
    void invalidStatusTransition_ThrowsException() {
        Order pendingOrder = Order.builder()
                .orderId(orderId)
                .orderNumber("ORD-20241215-ABCDE")
                .orderStatusCode(pendingStatus)
                .build();

        given(orderRepository.findById(orderId)).willReturn(Optional.of(pendingOrder));
        assertThatThrownBy(() -> adminOrderService.completeOrder(orderId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("수락된 주문만 완료할 수 있습니다. 현재 상태: PENDING");
    }

    @Test
    @DisplayName("동시성 테스트 - 같은 주문에 대한 동시 상태 변경")
    void concurrentStatusUpdate_ShouldHandleGracefully() {
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderStatusCodeRepository.findByCode("CONFIRMED")).willReturn(Optional.of(confirmedStatus));
        given(orderRepository.save(any(Order.class))).willReturn(order);

        AdminOrderResponseDto response1 = adminOrderService.confirmOrder(orderId);

        Order alreadyConfirmedOrder = Order.builder()
                .orderId(orderId)
                .orderNumber("ORD-20241215-ABCDE")
                .orderStatusCode(confirmedStatus)
                .build();

        given(orderRepository.findById(orderId)).willReturn(Optional.of(alreadyConfirmedOrder));

        assertThatThrownBy(() -> adminOrderService.confirmOrder(orderId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("결제 완료된 주문만 수락할 수 있습니다. 현재 상태: CONFIRMED");
    }
}
