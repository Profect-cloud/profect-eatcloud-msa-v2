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
    private final PaymentEventProducer paymentEventProducer;
    
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
        
        // Kafka 이벤트로 결제 완료 알림 (비동기)
        try {
            paymentEventProducer.publishPaymentCompleted(
                savedPayment.getOrderId(),
                savedPayment.getCustomerId(),
                savedPayment.getPaymentId(),
                savedPayment.getTotalAmount(), // totalAmount (총 주문 금액)
                savedPayment.getTotalAmount(), // amount (실제 결제 금액은 주문 서비스에서 계산)
                0, // pointsUsed (주문 서비스에서 조회)
                savedPayment.getPaymentMethod().toString(),
                savedPayment.getPgTransactionId()
            );
            log.info("결제 완료 이벤트 발행 성공: orderId={}, paymentId={}", 
                    savedPayment.getOrderId(), savedPayment.getPaymentId());
        } catch (Exception e) {
            log.error("결제 완료 이벤트 발행 실패: orderId={}, paymentId={}", 
                     savedPayment.getOrderId(), savedPayment.getPaymentId(), e);
            // 이벤트 발행 실패는 결제 완료를 막지 않음
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

        // Kafka 이벤트로 결제 완료 알림 (비동기)
        try {
            paymentEventProducer.publishPaymentCompleted(
                savedPayment.getOrderId(),
                savedPayment.getCustomerId(),
                savedPayment.getPaymentId(),
                savedPayment.getTotalAmount(), // totalAmount (총 주문 금액)
                savedPayment.getTotalAmount(), // amount (실제 결제 금액은 주문 서비스에서 계산)
                0, // pointsUsed (주문 서비스에서 조회)
                savedPayment.getPaymentMethod().toString(),
                savedPayment.getPgTransactionId()
            );
            log.info("[MOCK] 결제 완료 이벤트 발행 성공: orderId={}, paymentId={}", 
                    savedPayment.getOrderId(), savedPayment.getPaymentId());
        } catch (Exception e) {
            log.error("[MOCK] 결제 완료 이벤트 발행 실패: orderId={}, paymentId={}", 
                     savedPayment.getOrderId(), savedPayment.getPaymentId(), e);
            // 이벤트 발행 실패는 결제 완료를 막지 않음
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

    @Transactional
    public void cancelPaymentByOrder(UUID orderId, String cancelReason) {
        log.info("주문 취소로 인한 결제 처리: orderId={}, reason={}", orderId, cancelReason);

        // 1. PaymentRequest 취소 처리
        paymentRequestRepository.findByOrderId(orderId)
                .ifPresent(paymentRequest -> {
                    if (paymentRequest.getStatus() == PaymentRequestStatus.PENDING) {
                        paymentRequest.updateStatus(PaymentRequestStatus.CANCELLED);
                        paymentRequestRepository.save(paymentRequest);
                        log.info("결제 요청 취소 완료: orderId={}, paymentRequestId={}", 
                                orderId, paymentRequest.getPaymentRequestId());
                    }
                });

        // 2. 완료된 Payment가 있다면 환불 처리
        // paymentRepository.findByOrderId(orderId)
        //         .ifPresent(payment -> {
        //             if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
        //                 payment.setPaymentStatus(PaymentStatus.CANCELLED);
        //                 payment.setCancelledAt(LocalDateTime.now());
        //                 paymentRepository.save(payment);
        //
        //                 log.info("결제 취소 완료: orderId={}, paymentId={}", orderId, payment.getPaymentId());
        //
        //                 // TODO: 실제 PG사 환불 API 호출 (Toss 등)
        //                 // tossPaymentService.cancelPayment(payment.getPgTransactionId(), cancelReason);
        //             }
        //         });
        //
        // log.info("주문 취소로 인한 결제 처리 완료: orderId={}", orderId);
    }
} 
