package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.entity.Order;
import com.eatcloud.orderservice.entity.OrderStatusCode;
import com.eatcloud.orderservice.event.PaymentCompletedEvent;
import com.eatcloud.orderservice.event.PointDeductionRequestEvent;
import com.eatcloud.orderservice.repository.OrderRepository;
import com.eatcloud.orderservice.repository.OrderStatusCodeRepository;
import org.springframework.kafka.core.KafkaTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncOrderCompletionService {

    private final OrderRepository orderRepository;
    private final OrderStatusCodeRepository orderStatusCodeRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public void processOrderCompletion(PaymentCompletedEvent event) {
        log.info("결제 완료 후 비동기 처리 시작: orderId={}, pointsUsed={}", 
                event.getOrderId(), event.getPointsUsed());

        try {
            updateOrderStatusToPaid(event.getOrderId());

            Order order = orderRepository.findById(event.getOrderId())
                    .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + event.getOrderId()));
            
            Integer pointsUsed = order.getPointsToUse();
            log.info("주문에서 포인트 사용 정보 조회: orderId={}, pointsUsed={}", 
                    event.getOrderId(), pointsUsed);

            if (pointsUsed != null && pointsUsed > 0) {
                publishPointDeductionEvent(event.getOrderId(), event.getCustomerId(), pointsUsed);
            } else {
                log.info("사용된 포인트가 없어 포인트 처리 건너뜀: orderId={}", event.getOrderId());
            }

            log.info("결제 완료 후 비동기 처리 완료: orderId={}", event.getOrderId());
            
        } catch (Exception e) {
            log.error("결제 완료 후 비동기 처리 실패: orderId={}", event.getOrderId(), e);
            throw e;
        }
    }

    private void updateOrderStatusToPaid(UUID orderId) {
        log.info("주문 상태를 PAID로 변경: orderId={}", orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다: " + orderId));

        OrderStatusCode paidStatus = orderStatusCodeRepository.findByCode("PAID")
                .orElseThrow(() -> new RuntimeException("PAID 상태 코드를 찾을 수 없습니다"));
        
        order.setOrderStatusCode(paidStatus);
        orderRepository.save(order);
        
        log.info("주문 상태 PAID로 변경 완료: orderId={}", orderId);
    }

    private void publishPointDeductionEvent(UUID orderId, UUID customerId, Integer pointsUsed) {
        log.info("포인트 차감 이벤트 발행: orderId={}, customerId={}, pointsUsed={}", 
                orderId, customerId, pointsUsed);

        try {
            PointDeductionRequestEvent event = PointDeductionRequestEvent.builder()
                    .orderId(orderId)
                    .customerId(customerId)
                    .pointsUsed(pointsUsed)
                    .sagaId(UUID.randomUUID().toString())
                    .build();

            kafkaTemplate.send("point.deduction.request", orderId.toString(), event);
            log.info("포인트 차감 이벤트 발행 완료: orderId={}, customerId={}, pointsUsed={}", 
                    orderId, customerId, pointsUsed);

        } catch (Exception e) {
            log.error("포인트 차감 이벤트 발행 실패: orderId={}, customerId={}, pointsUsed={}", 
                     orderId, customerId, pointsUsed, e);
            throw new RuntimeException("포인트 차감 이벤트 발행 실패: " + e.getMessage(), e);
        }
    }

}
