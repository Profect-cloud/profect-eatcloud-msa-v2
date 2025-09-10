package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.event.PaymentRequestResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRequestResponseEventConsumer {

    // Saga ID별로 CompletableFuture를 저장하는 맵
    private final ConcurrentHashMap<String, CompletableFuture<PaymentRequestResponseEvent>> pendingRequests = new ConcurrentHashMap<>();

    @KafkaListener(topics = "payment.request.response", groupId = "order-service", containerFactory = "paymentRequestKafkaListenerContainerFactory")
    public void handlePaymentRequestResponse(PaymentRequestResponseEvent event) {
        log.info("PaymentRequestResponseEvent 수신: orderId={}, customerId={}, sagaId={}, success={}", 
                event.getOrderId(), event.getCustomerId(), event.getSagaId(), event.isSuccess());
        
        // 해당 Saga ID의 CompletableFuture를 완료
        CompletableFuture<PaymentRequestResponseEvent> future = pendingRequests.remove(event.getSagaId());
        if (future != null) {
            future.complete(event);
            log.info("결제 요청 응답 처리 완료: sagaId={}", event.getSagaId());
        } else {
            log.warn("대기 중인 결제 요청을 찾을 수 없음: sagaId={}", event.getSagaId());
        }
    }

    /**
     * 결제 요청 응답을 기다림
     */
    public CompletableFuture<PaymentRequestResponseEvent> waitForResponse(String sagaId) {
        CompletableFuture<PaymentRequestResponseEvent> future = new CompletableFuture<>();
        pendingRequests.put(sagaId, future);
        return future;
    }
}
