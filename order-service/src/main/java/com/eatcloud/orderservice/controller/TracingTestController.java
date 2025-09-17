package com.eatcloud.orderservice.controller;

import com.eatcloud.logging.mdc.MDCUtil;
import com.eatcloud.orderservice.service.ExternalApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Slf4j
public class TracingTestController {

    private final ExternalApiService externalApiService;

    @GetMapping("/tracing/{customerId}")
    public ResponseEntity<Map<String, Object>> testTracing(@PathVariable UUID customerId) {
        Map<String, Object> response = new HashMap<>();
        
        log.info("🚀 분산 추적 테스트 시작");
        log.info("현재 traceId: {}", MDCUtil.getRequestId());
        log.info("현재 userId: {}", MDCUtil.getUserId());
        
        try {
            // RestTemplate 호출 테스트 (Customer Service)
            log.info("Customer Service 호출 시작");
            Boolean customerExists = externalApiService.checkCustomerExists(customerId);
            log.info("Customer Service 호출 결과: {}", customerExists);
            
            // Store Service 호출 테스트
            log.info("Store Service 호출 시작");
            UUID menuId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
            Integer menuPrice = externalApiService.getMenuPrice(menuId);
            log.info("Store Service 호출 결과: {}", menuPrice);
            
            response.put("success", true);
            response.put("traceId", MDCUtil.getRequestId());
            response.put("customerExists", customerExists);
            response.put("menuPrice", menuPrice);
            response.put("message", "분산 추적 테스트 완료!");
            
            log.info("🎉 분산 추적 테스트 성공 완료");
            
        } catch (Exception e) {
            log.error("분산 추적 테스트 중 오류 발생", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/kafka-test")
    public ResponseEntity<Map<String, Object>> testKafkaTracing() {
        Map<String, Object> response = new HashMap<>();
        
        log.info("🚀 Kafka 분산 추적 테스트 시작");
        log.info("현재 traceId: {}", MDCUtil.getRequestId());
        
        // TODO: Kafka 이벤트 발행 테스트 추가
        // orderEventProducer.publishOrderCreated(testEvent);
        
        response.put("success", true);
        response.put("traceId", MDCUtil.getRequestId());
        response.put("message", "Kafka 분산 추적 테스트 준비 완료!");
        
        log.info("🎉 Kafka 분산 추적 테스트 완료");
        
        return ResponseEntity.ok(response);
    }
}
