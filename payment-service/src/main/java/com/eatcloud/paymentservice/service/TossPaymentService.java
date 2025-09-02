package com.eatcloud.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TossPaymentService {
    
    private final WebClient tossWebClient;
    
    @Value("${toss.secret-key}")
    private String secretKey;
    
    public Map<String, Object> confirmPayment(String paymentKey, String orderId, Integer amount) {
        log.info("토스페이먼츠 결제 승인 요청: paymentKey={}, orderId={}, amount={}", paymentKey, orderId, amount);
        
        String encodedAuth = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes());
        
        Map<String, Object> request = Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "amount", amount
        );
        
        try {
            Map<String, Object> response = tossWebClient
                    .post()
                    .uri("/payments/confirm")
                    .header("Authorization", "Basic " + encodedAuth)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse ->
                            clientResponse.bodyToMono(String.class).flatMap(body -> {
                                log.error("토스페이먼츠 결제 승인 오류: status={}, body={}", clientResponse.statusCode(), body);
                                return Mono.error(new RuntimeException("TOSS_CONFIRM_ERROR: " + body));
                            })
                    )
                    .bodyToMono(Map.class)
                    .block();

            log.info("토스페이먼츠 결제 승인 성공: orderId={}", orderId);
            return response;

        } catch (WebClientResponseException wce) {
            log.error("토스페이먼츠 결제 승인 실패(HTTP): status={}, response={}", wce.getRawStatusCode(), wce.getResponseBodyAsString());
            throw new RuntimeException("결제 승인 중 오류가 발생했습니다: " + wce.getResponseBodyAsString(), wce);
        } catch (Exception e) {
            log.error("토스페이먼츠 결제 승인 실패: orderId={}", orderId, e);
            throw new RuntimeException("결제 승인 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    public String createPaymentRequest(String orderId, Integer amount, String customerId) {
        log.info("토스페이먼츠 결제 요청 생성: orderId={}, amount={}, customerId={}", orderId, amount, customerId);

        String redirectUrl = String.format(
                "https://pay.toss.im/?orderId=%s&amount=%d&customerId=%s",
                orderId, amount, customerId
        );
        
        log.info("토스페이먼츠 결제 요청 URL 생성: {}", redirectUrl);
        return redirectUrl;
    }
} 
