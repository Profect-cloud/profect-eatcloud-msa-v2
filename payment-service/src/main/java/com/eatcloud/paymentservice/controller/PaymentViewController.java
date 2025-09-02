package com.eatcloud.paymentservice.controller;

import com.eatcloud.paymentservice.entity.Payment;
import com.eatcloud.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PaymentViewController {

    private final PaymentService paymentService;

    @Value("${toss.client-key:}")
    private String tossClientKey;

    @GetMapping("/payments/charge")
    public String chargePage(Model model) {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Integer amount = 1000;

        try {
            paymentService.createPaymentRequest(orderId, userId, amount);
        } catch (Exception e) {
            log.warn("결제요청 생성 중 경고: {}", e.getMessage());
        }

        model.addAttribute("clientKey", tossClientKey);
        model.addAttribute("orderId", orderId.toString());
        model.addAttribute("userId", userId.toString());
        model.addAttribute("amount", amount);
        return "payment/checkout";
    }

    @GetMapping("/api/v1/payment/success")
    public String paymentSuccess(
            @RequestParam("paymentKey") String paymentKey,
            @RequestParam("orderId") String orderId,
            @RequestParam("amount") Integer amount,
            Model model
    ) {
        try {
            Payment payment = paymentService.confirmPayment(paymentKey, orderId, amount);

            model.addAttribute("orderId", payment.getOrderId().toString());
            model.addAttribute("amount", payment.getTotalAmount());
            model.addAttribute("status", payment.getPaymentStatus().name());
            model.addAttribute("method", payment.getPaymentMethod() != null ? payment.getPaymentMethod().getDisplayName() : "");
            model.addAttribute("approvedAt", payment.getApprovedAt() != null ? payment.getApprovedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
            model.addAttribute("paymentKey", paymentKey);

            return "payment/success";
        } catch (Exception e) {
            log.error("결제 성공 콜백 처리 중 오류", e);
            model.addAttribute("message", "결제 승인 처리 중 오류가 발생했습니다.");
            model.addAttribute("code", "APPROVAL_ERROR");
            model.addAttribute("orderId", orderId);
            model.addAttribute("error", e.getMessage());
            return "payment/fail";
        }
    }

    @GetMapping("/api/v1/payment/fail")
    public String paymentFail(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "orderId", required = false) String orderId,
            @RequestParam(value = "paymentKey", required = false) String paymentKey,
            @RequestParam(value = "amount", required = false) Integer amount,
            Model model
    ) {
        model.addAttribute("code", code);
        model.addAttribute("message", message);
        model.addAttribute("orderId", orderId);
        model.addAttribute("paymentKey", paymentKey);
        model.addAttribute("amount", amount);
        model.addAttribute("rollbackCompleted", false);
        return "payment/fail";
    }

    @GetMapping("/api/v1/payment/cancel")
    public String paymentCancel(
            @RequestParam(value = "message", required = false, defaultValue = "고객 요청에 의한 취소") String message,
            @RequestParam(value = "orderId", required = false) String orderId,
            @RequestParam(value = "paymentKey", required = false) String paymentKey,
            @RequestParam(value = "amount", required = false) Integer amount,
            Model model
    ) {
        model.addAttribute("message", message);
        model.addAttribute("orderId", orderId);
        model.addAttribute("paymentKey", paymentKey);
        model.addAttribute("amount", amount);
        model.addAttribute("rollbackCompleted", true);
        return "payment/cancel";
    }
}


