package com.eatcloud.paymentservice.service;

import com.eatcloud.paymentservice.entity.Payment;
import com.eatcloud.paymentservice.entity.PaymentMethodCode;
import com.eatcloud.paymentservice.entity.PaymentRequest;
import com.eatcloud.paymentservice.entity.PaymentStatus;
import com.eatcloud.paymentservice.entity.PaymentRequestStatus;
import com.eatcloud.paymentservice.repository.PaymentMethodCodeRepository;
import com.eatcloud.paymentservice.repository.PaymentRepository;
import com.eatcloud.paymentservice.repository.PaymentRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    
    private final PaymentRepository paymentRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final TossPaymentService tossPaymentService;
    private final PaymentMethodCodeRepository paymentMethodCodeRepository;
    private final RestTemplate restTemplate;
    
    private static final long PAYMENT_TIMEOUT_MINUTES = 5;
    
    @Transactional
    public PaymentRequest createPaymentRequest(UUID orderId, UUID customerId, Integer amount) {
        log.info("결제 요청 생성: orderId={}, customerId={}, amount={}", orderId, customerId, amount);
        
        String redirectUrl = tossPaymentService.createPaymentRequest(
                orderId.toString(), amount, customerId.toString()
        );
        
        PaymentRequest paymentRequest = PaymentRequest.builder()
                .orderId(orderId)
                .customerId(customerId)
                .pgProvider("TOSS")
                .requestPayload("{}")
                .redirectUrl(redirectUrl)
                .status(PaymentRequestStatus.PENDING)
                .timeoutAt(LocalDateTime.now().plusMinutes(PAYMENT_TIMEOUT_MINUTES))
                .build();
        
        PaymentRequest savedRequest = paymentRequestRepository.save(paymentRequest);
        
        schedulePaymentTimeout(savedRequest.getPaymentRequestId());
        
        log.info("결제 요청 생성 완료: paymentRequestId={}, redirectUrl={}", 
                savedRequest.getPaymentRequestId(), redirectUrl);
        
        return savedRequest;
    }
    
    @Transactional
    public Payment confirmPayment(String paymentKey, String orderId, Integer amount) {
        log.info("결제 승인 처리: paymentKey={}, orderId={}, amount={}", paymentKey, orderId, amount);
        
        Map<String, Object> tossResponse = tossPaymentService.confirmPayment(paymentKey, orderId, amount);
        
        PaymentRequest paymentRequest = paymentRequestRepository.findByOrderId(UUID.fromString(orderId))
                .orElseThrow(() -> new RuntimeException("결제 요청을 찾을 수 없습니다: " + orderId));
        
        Payment payment = Payment.builder()
                .orderId(UUID.fromString(orderId))
                .customerId(paymentRequest.getCustomerId())
                .totalAmount(amount)
                .pgTransactionId(paymentKey)
                .approvalCode(orderId)
                .paymentStatus(PaymentStatus.COMPLETED)
                .paymentMethod(mapTossMethodToPaymentMethod((String) tossResponse.get("method")))
                .approvedAt(LocalDateTime.now())
                .build();
        
        Payment savedPayment = paymentRepository.save(payment);
        
        paymentRequest.updateStatus(PaymentRequestStatus.COMPLETED);
        paymentRequestRepository.save(paymentRequest);
        
        // 주문 서비스에 동기 호출로 결제 완료 알림
        try {
            String url = "http://order-service/api/v1/orders/" + savedPayment.getOrderId() + "/payment/complete";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Name", "payment-service");
            
            Map<String, Object> requestBody = Map.of("paymentId", savedPayment.getPaymentId().toString());
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("주문 서비스에 결제 완료 알림 성공: orderId={}, paymentId={}", 
                        savedPayment.getOrderId(), savedPayment.getPaymentId());
            } else {
                log.error("주문 서비스에 결제 완료 알림 실패: orderId={}, paymentId={}, status={}", 
                         savedPayment.getOrderId(), savedPayment.getPaymentId(), response.getStatusCode());
                throw new RuntimeException("주문 상태 업데이트 실패: " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("주문 서비스 호출 실패: orderId={}, paymentId={}", 
                     savedPayment.getOrderId(), savedPayment.getPaymentId(), e);
            throw new RuntimeException("결제는 완료되었으나 주문 상태 업데이트에 실패했습니다: " + e.getMessage(), e);
        }
        
        log.info("결제 승인 완료: paymentId={}, orderId={}", savedPayment.getPaymentId(), orderId);
        
        return savedPayment;
    }

    @Transactional
    public Payment confirmPaymentMock(String paymentKey, String orderId, Integer amount, UUID optionalCustomerId) {
        log.info("[MOCK] 결제 승인 처리: paymentKey={}, orderId={}, amount={}", paymentKey, orderId, amount);

        UUID orderUuid = UUID.fromString(orderId);
        PaymentRequest paymentRequest = paymentRequestRepository.findByOrderId(orderUuid)
                .orElse(null);

        UUID customerId = optionalCustomerId;
        if (customerId == null) {
            if (paymentRequest != null) {
                customerId = paymentRequest.getCustomerId();
            } else {
                customerId = UUID.randomUUID();
            }
        }

        Payment payment = Payment.builder()
                .orderId(orderUuid)
                .customerId(customerId)
                .totalAmount(amount)
                .pgTransactionId(paymentKey != null ? paymentKey : orderId)
                .approvalCode(orderId)
                .paymentStatus(PaymentStatus.COMPLETED)
                .paymentMethod(getMethodByCodeOrThrow("CARD"))
                .approvedAt(LocalDateTime.now())
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        if (paymentRequest != null) {
            paymentRequest.updateStatus(PaymentRequestStatus.COMPLETED);
            paymentRequestRepository.save(paymentRequest);
        }

        // 주문 서비스에 동기 호출로 결제 완료 알림
        try {
            String url = "http://order-service/api/v1/orders/" + savedPayment.getOrderId() + "/payment/complete";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Name", "payment-service");
            
            Map<String, Object> requestBody = Map.of("paymentId", savedPayment.getPaymentId().toString());
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[MOCK] 주문 서비스에 결제 완료 알림 성공: orderId={}, paymentId={}", 
                        savedPayment.getOrderId(), savedPayment.getPaymentId());
            } else {
                log.error("[MOCK] 주문 서비스에 결제 완료 알림 실패: orderId={}, paymentId={}, status={}", 
                         savedPayment.getOrderId(), savedPayment.getPaymentId(), response.getStatusCode());
                throw new RuntimeException("주문 상태 업데이트 실패: " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("[MOCK] 주문 서비스 호출 실패: orderId={}, paymentId={}", 
                     savedPayment.getOrderId(), savedPayment.getPaymentId(), e);
            throw new RuntimeException("결제는 완료되었으나 주문 상태 업데이트에 실패했습니다: " + e.getMessage(), e);
        }

        log.info("[MOCK] 결제 승인 완료: paymentId={}, orderId={}", savedPayment.getPaymentId(), orderId);
        return savedPayment;
    }

    private PaymentMethodCode getMethodByCodeOrThrow(String code) {
        return paymentMethodCodeRepository.findById(code)
            .orElseThrow(() -> new IllegalStateException("결제수단 코드가 없습니다: " + code));
    }

    private PaymentMethodCode mapTossMethodToPaymentMethod(String tossMethod) {
        if (tossMethod == null) return getMethodByCodeOrThrow("CARD");

        String code = switch (tossMethod) {
            case "카드" -> "CARD";
            case "가상계좌" -> "VIRTUAL_ACCOUNT";
            case "계좌이체" -> "TRANSFER";
            case "휴대폰" -> "PHONE";
            case "상품권", "도서문화상품권", "게임문화상품권" -> "GIFT_CERTIFICATE";
            default -> "CARD";
        };
        return getMethodByCodeOrThrow(code);
    }
    
    @Async
    public CompletableFuture<Void> schedulePaymentTimeout(UUID paymentRequestId) {
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(PAYMENT_TIMEOUT_MINUTES * 60 * 1000);

                updateExpiredPaymentRequest(paymentRequestId);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("결제 타임아웃 작업이 중단되었습니다", e);
            } catch (Exception e) {
                log.error("결제 타임아웃 작업 중 오류 발생", e);
            }
        });
    }
    
    @Transactional
    public void updateExpiredPaymentRequest(UUID paymentRequestId) {
        paymentRequestRepository.findById(paymentRequestId)
                .ifPresent(paymentRequest -> {
                    if (PaymentRequestStatus.PENDING.equals(paymentRequest.getStatus())) {
                        paymentRequest.updateStatus(PaymentRequestStatus.TIMEOUT);
                        paymentRequest.setFailureReason("결제 타임아웃 - 5분 내 응답 없음");
                        paymentRequestRepository.save(paymentRequest);
                        
                        log.info("결제 요청 타임아웃 처리: paymentRequestId={}", paymentRequestId);
                    }
                });
    }

    @Transactional
    public void updatePaymentStatusToRefunded(UUID orderId) {
        log.info("결제 상태를 REFUNDED로 업데이트: orderId={}", orderId);
        
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("결제 정보를 찾을 수 없습니다: " + orderId));
        
        payment.updateStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        
        log.info("결제 상태 REFUNDED로 업데이트 완료: paymentId={}, orderId={}", 
                payment.getPaymentId(), orderId);
    }
} 
