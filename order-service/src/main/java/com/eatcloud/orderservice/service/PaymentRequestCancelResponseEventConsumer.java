package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.event.PaymentRequestCancelResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRequestCancelResponseEventConsumer {

    // Saga ID별로 CompletableFuture를 저장하는 맵
    private final ConcurrentHashMap<String, CompletableFuture<PaymentRequestCancelResponseEvent>> pendingRequests = new ConcurrentHashMap<>();

    @KafkaListener(topics = "payment.request.cancel.response", groupId = "order-service", containerFactory = "paymentRequestCancelKafkaListenerContainerFactory")
    public void handlePaymentRequestCancelResponse(PaymentRequestCancelResponseEvent event) {
        log.info("PaymentRequestCancelResponseEvent 수신: orderId={}, sagaId={}, success={}", 
                event.getOrderId(), event.getSagaId(), event.isSuccess());
        
        // 해당 Saga ID의 CompletableFuture를 완료
        CompletableFuture<PaymentRequestCancelResponseEvent> future = pendingRequests.remove(event.getSagaId());
        if (future != null) {
            future.complete(event);
            log.info("결제 요청 취소 응답 처리 완료: sagaId={}", event.getSagaId());
        } else {
            log.warn("대기 중인 결제 요청 취소를 찾을 수 없음: sagaId={}", event.getSagaId());
        }
    }

    /**
     * 결제 요청 취소 응답을 기다림
     */
    public CompletableFuture<PaymentRequestCancelResponseEvent> waitForResponse(String sagaId) {
        CompletableFuture<PaymentRequestCancelResponseEvent> future = new CompletableFuture<>();
        pendingRequests.put(sagaId, future);
        return future;
    }
}
