package com.eatcloud.orderservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eatcloud.logging.annotation.Loggable;
import com.eatcloud.orderservice.entity.Order;
import com.eatcloud.orderservice.repository.OrderRepository;
import com.eatcloud.orderservice.dto.response.AdminOrderResponseDto;
import com.eatcloud.orderservice.entity.OrderStatusCode;
import com.eatcloud.orderservice.repository.OrderStatusCodeRepository;

import java.util.UUID;
@Service
@RequiredArgsConstructor
@Transactional
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusCodeRepository orderStatusCodeRepository;

    public AdminOrderResponseDto confirmOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderId));

        if (!"PAID".equals(order.getOrderStatusCode().getCode())) {
            throw new RuntimeException("결제 완료된 주문만 수락할 수 있습니다. 현재 상태: " + order.getOrderStatusCode().getCode());
        }

        OrderStatusCode confirmedStatus = orderStatusCodeRepository.findByCode("CONFIRMED")
                .orElseThrow(() -> new RuntimeException("주문 상태 코드를 찾을 수 없습니다: CONFIRMED"));

        order.setOrderStatusCode(confirmedStatus);
        orderRepository.save(order);

        return AdminOrderResponseDto.builder()
                .orderId(orderId)
                .orderNumber(order.getOrderNumber())
                .orderStatus("CONFIRMED")
                .message("주문이 수락되었습니다.")
                .build();
    }


    public AdminOrderResponseDto completeOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderId));

        if (!"CONFIRMED".equals(order.getOrderStatusCode().getCode())) {
            throw new RuntimeException("수락된 주문만 완료할 수 있습니다. 현재 상태: " + order.getOrderStatusCode().getCode());
        }

        OrderStatusCode completedStatus = orderStatusCodeRepository.findByCode("COMPLETED")
                .orElseThrow(() -> new RuntimeException("주문 상태 코드를 찾을 수 없습니다: COMPLETED"));

        order.setOrderStatusCode(completedStatus);
        orderRepository.save(order);

        return AdminOrderResponseDto.builder()
                .orderId(orderId)
                .orderNumber(order.getOrderNumber())
                .orderStatus("COMPLETED")
                .message("주문이 완료되었습니다.")
                .build();
    }


    @Transactional(readOnly = true)
    public AdminOrderResponseDto getOrderStatus(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderId));

        return AdminOrderResponseDto.builder()
                .orderId(orderId)
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getOrderStatusCode().getCode())
                .message("주문 상태 조회 완료")
                .build();
    }
}
