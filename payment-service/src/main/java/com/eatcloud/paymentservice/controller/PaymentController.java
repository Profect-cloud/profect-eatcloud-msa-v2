package com.eatcloud.paymentservice.controller;

import com.eatcloud.logging.annotation.Loggable;
import com.eatcloud.paymentservice.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Service", description = "결제 서비스 API")
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true, maskSensitiveData = true)
public class PaymentController {
    
    private final PaymentService paymentService;
    @Value("${payment.mock.enabled:false}")
    private boolean mockEnabled;
    
    @PostMapping("/confirm")
    @Operation(summary = "결제 승인", description = "토스페이먼츠 결제 승인을 처리합니다.")
    public ResponseEntity<String> confirmPayment(@RequestBody Map<String, Object> request,
                                               @AuthenticationPrincipal Jwt jwt) {
        try {
            UUID customerId = UUID.fromString(jwt.getSubject());
            
            String paymentKey = (String) request.get("paymentKey");
            String orderId = (String) request.get("orderId");
            Integer amount = (Integer) request.get("amount");
            
            log.info("결제 승인 요청: customerId={}, paymentKey={}, orderId={}, amount={}", 
                    customerId, paymentKey, orderId, amount);
            
            if (mockEnabled) {
                paymentService.confirmPaymentMock(paymentKey, orderId, amount, customerId);
            } else {
                paymentService.confirmPayment(paymentKey, orderId, amount);
            }
            
            return ResponseEntity.ok("결제가 성공적으로 처리되었습니다.");
            
        } catch (Exception e) {
            log.error("결제 승인 실패", e);
            return ResponseEntity.badRequest().body("결제 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @GetMapping("/status/{orderId}")
    @Operation(summary = "결제 상태 확인", description = "주문 ID로 결제 상태를 조회합니다.")
    public ResponseEntity<String> getPaymentStatus(@PathVariable String orderId,
                                                 @AuthenticationPrincipal Jwt jwt) {
        try {
            UUID customerId = UUID.fromString(jwt.getSubject()); // JWT의 sub 클레임에서 customerId 추출
            
            log.info("결제 상태 확인 요청: customerId={}, orderId={}", customerId, orderId);
            return ResponseEntity.ok("결제 상태 확인 API - 구현 예정");
            
        } catch (Exception e) {
            log.error("결제 상태 확인 실패", e);
            return ResponseEntity.badRequest().body("결제 상태 확인 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @PostMapping("/refund/{orderId}")
    @Operation(summary = "결제 환불 상태 업데이트", description = "주문 ID로 결제 상태를 REFUNDED로 업데이트합니다.")
    public ResponseEntity<String> updatePaymentStatusToRefunded(@PathVariable String orderId,
                                                              @AuthenticationPrincipal Jwt jwt) {
        try {
            UUID customerId = UUID.fromString(jwt.getSubject());
            
            log.info("결제 환불 상태 업데이트 요청: customerId={}, orderId={}", customerId, orderId);
            
            paymentService.updatePaymentStatusToRefunded(UUID.fromString(orderId));
            
            return ResponseEntity.ok("결제 상태가 REFUNDED로 업데이트되었습니다.");
            
        } catch (Exception e) {
            log.error("결제 환불 상태 업데이트 실패", e);
            return ResponseEntity.badRequest().body("결제 환불 상태 업데이트 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @PostMapping("/request")
    @Operation(summary = "결제 요청 생성", description = "주문에 대한 결제 요청을 생성합니다.")
    public ResponseEntity<Map<String, Object>> createPaymentRequest(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal Jwt jwt) {
        
        try {
            UUID customerId = UUID.fromString(jwt.getSubject());
            log.info("결제 요청: customerId={}", customerId);
            
            UUID orderId = UUID.fromString((String) request.get("orderId"));
            Integer amount = (Integer) request.get("amount");
            
            log.info("결제 요청 생성: orderId={}, customerId={}, amount={}", orderId, customerId, amount);
            
            var paymentRequest = paymentService.createPaymentRequest(orderId, customerId, amount);
            String paymentUrl = paymentRequest.getRedirectUrl();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "paymentUrl", paymentUrl,
                "orderId", orderId.toString(),
                "amount", amount
            ));
            
        } catch (Exception e) {
            log.error("결제 요청 생성 실패", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "결제 요청 생성 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }
} 