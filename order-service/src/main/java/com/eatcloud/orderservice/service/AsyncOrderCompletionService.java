package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncOrderCompletionService {

    private final RestTemplate restTemplate;

    /**
     * 결제 완료 후 비동기 후처리 (포인트 차감)
     */
    public void processOrderCompletion(PaymentCompletedEvent event) {
        log.info("결제 완료 후 비동기 처리 시작: orderId={}, pointsUsed={}", 
                event.getOrderId(), event.getPointsUsed());

        // 포인트 사용이 있는 경우에만 처리
        if (event.getPointsUsed() != null && event.getPointsUsed() > 0) {
            processPointDeduction(event);
        } else {
            log.info("사용된 포인트가 없어 포인트 처리 건너뜀: orderId={}", event.getOrderId());
        }

        log.info("결제 완료 후 비동기 처리 완료: orderId={}", event.getOrderId());
    }

    /**
     * 포인트 실제 차감 (재시도 포함)
     */
    @Retryable(
        retryFor = {RestClientException.class}, 
        maxAttempts = 5,
        backoff = @Backoff(delay = 2000, multiplier = 2) // 2s, 4s, 8s, 16s, 32s
    )
    public void processPointDeduction(PaymentCompletedEvent event) {
        log.info("포인트 차감 처리 시작: orderId={}, customerId={}, pointsUsed={}", 
                event.getOrderId(), event.getCustomerId(), event.getPointsUsed());

        try {
            String url = String.format("http://customer-service/api/v1/customers/%s/points/process-reservation", 
                                     event.getCustomerId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Name", "order-service");
            headers.set("X-Customer-Id", event.getCustomerId().toString());

            Map<String, Object> requestBody = Map.of(
                "orderId", event.getOrderId().toString(),
                "points", event.getPointsUsed()
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("포인트 차감 완료: orderId={}, customerId={}, pointsUsed={}", 
                        event.getOrderId(), event.getCustomerId(), event.getPointsUsed());
            } else {
                log.error("포인트 차감 실패: orderId={}, customerId={}, status={}", 
                         event.getOrderId(), event.getCustomerId(), response.getStatusCode());
                throw new RestClientException("포인트 차감 API 호출 실패: " + response.getStatusCode());
            }

        } catch (RestClientException e) {
            log.error("포인트 차감 API 호출 실패: orderId={}, customerId={}, pointsUsed={}", 
                     event.getOrderId(), event.getCustomerId(), event.getPointsUsed(), e);
            throw e; // 재시도를 위해 예외 재발생
        }
    }

    /**
     * 포인트 차감 최종 실패 시 복구 처리
     */
    @Recover
    public void recoverPointDeduction(RestClientException ex, PaymentCompletedEvent event) {
        log.error("포인트 차감 최종 실패 - 수동 처리 필요: orderId={}, customerId={}, pointsUsed={}", 
                 event.getOrderId(), event.getCustomerId(), event.getPointsUsed(), ex);

        // 중요: 결제는 이미 완료되었지만 포인트 차감 실패
        // 관리자에게 알림 및 수동 처리 요청
        sendCriticalAlert("포인트 차감 실패", String.format(
            "결제 완료 후 포인트 차감 실패 - 수동 처리 필요\n" +
            "주문 ID: %s\n" +
            "고객 ID: %s\n" + 
            "사용 포인트: %d\n" +
            "에러: %s",
            event.getOrderId(), event.getCustomerId(), event.getPointsUsed(), ex.getMessage()
        ));
    }

    /**
     * 중요 알림 전송 (로깅 기반)
     */
    private void sendCriticalAlert(String title, String message) {
        log.error("🚨 CRITICAL ALERT 🚨 [{}]: {}", title, message);
        // TODO: 실제 알림 시스템 (Slack, 이메일 등)과 연동
    }
}
