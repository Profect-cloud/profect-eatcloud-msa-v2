package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.event.PointReservationCancelResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointReservationCancelResponseEventConsumer {

    // Saga ID별로 CompletableFuture를 저장하는 맵
    private final ConcurrentHashMap<String, CompletableFuture<PointReservationCancelResponseEvent>> pendingRequests = new ConcurrentHashMap<>();

    @KafkaListener(topics = "point.reservation.cancel.response", groupId = "order-service", containerFactory = "pointReservationCancelKafkaListenerContainerFactory")
    public void handlePointReservationCancelResponse(PointReservationCancelResponseEvent event) {
        log.info("PointReservationCancelResponseEvent 수신: orderId={}, customerId={}, sagaId={}, success={}", 
                event.getOrderId(), event.getCustomerId(), event.getSagaId(), event.isSuccess());
        
        // 해당 Saga ID의 CompletableFuture를 완료
        CompletableFuture<PointReservationCancelResponseEvent> future = pendingRequests.remove(event.getSagaId());
        if (future != null) {
            future.complete(event);
            log.info("포인트 예약 취소 응답 처리 완료: sagaId={}", event.getSagaId());
        } else {
            log.warn("대기 중인 포인트 예약 취소 요청을 찾을 수 없음: sagaId={}", event.getSagaId());
        }
    }

    /**
     * 포인트 예약 취소 응답을 기다림
     */
    public CompletableFuture<PointReservationCancelResponseEvent> waitForResponse(String sagaId) {
        CompletableFuture<PointReservationCancelResponseEvent> future = new CompletableFuture<>();
        pendingRequests.put(sagaId, future);
        return future;
    }
}
