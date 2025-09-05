package com.eatcloud.paymentservice.service;

import com.eatcloud.paymentservice.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {
    
    private final PaymentService paymentService;
    
    // TODO: 오케스트레이션으로 대체됨 - 비활성화
    // @KafkaListener(topics = "order.created", groupId = "payment-service")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("주문 생성 이벤트 수신: orderId={}, customerId={}, finalAmount={}", 
                event.getOrderId(), event.getCustomerId(), event.getFinalAmount());
        
        try {
            paymentService.createPaymentRequest(
                    event.getOrderId(),
                    event.getCustomerId(),
                    event.getFinalAmount()
            );
            
            log.info("주문 생성 이벤트 처리 완료: orderId={}", event.getOrderId());
            
        } catch (Exception e) {
            log.error("주문 생성 이벤트 처리 실패: orderId={}", event.getOrderId(), e);
        }
    }
} 
