package com.eatcloud.orderservice.kafka.consumer;

import com.eatcloud.orderservice.event.PointReservationResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointReservationResponseEventConsumer {

    private final ConcurrentHashMap<String, CompletableFuture<PointReservationResponseEvent>> pendingRequests = new ConcurrentHashMap<>();

    @KafkaListener(topics = "point.reservation.response", groupId = "order-service", containerFactory = "pointReservationKafkaListenerContainerFactory")
    public void handlePointReservationResponse(PointReservationResponseEvent event) {
        log.info("PointReservationResponseEvent 수신: orderId={}, customerId={}, sagaId={}, success={}", 
                event.getOrderId(), event.getCustomerId(), event.getSagaId(), event.isSuccess());

        CompletableFuture<PointReservationResponseEvent> future = pendingRequests.remove(event.getSagaId());
        if (future != null) {
            future.complete(event);
            log.info("포인트 예약 응답 처리 완료: sagaId={}", event.getSagaId());
        } else {
            log.warn("대기 중인 포인트 예약 요청을 찾을 수 없음: sagaId={}", event.getSagaId());
        }
    }

    public CompletableFuture<PointReservationResponseEvent> waitForResponse(String sagaId) {
        CompletableFuture<PointReservationResponseEvent> future = new CompletableFuture<>();
        pendingRequests.put(sagaId, future);
        return future;
    }
}
