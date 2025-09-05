package com.eatcloud.customerservice.controller;

import com.eatcloud.customerservice.service.PointReservationService;
import com.eatcloud.customerservice.entity.PointReservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/points")
@RequiredArgsConstructor
@Slf4j
public class PointController {

    private final PointReservationService pointReservationService;

    /**
     * 포인트 예약 생성 (오케스트레이션용)
     */
    @PostMapping("/reserve")
    public ResponseEntity<Map<String, Object>> reservePoints(
            @PathVariable UUID customerId,
            @RequestBody ReservePointsRequest request,
            @RequestHeader(value = "X-Service-Name", required = false) String serviceName,
            @RequestHeader(value = "X-Customer-Id", required = false) String headerCustomerId) {
        
        try {
            log.info("포인트 예약 요청: customerId={}, orderId={}, points={}, serviceName={}", 
                    customerId, request.getOrderId(), request.getPoints(), serviceName);

            // 서비스 간 호출 검증
            if (!"order-service".equals(serviceName)) {
                log.warn("Unauthorized point reservation attempt from: {}", serviceName);
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized service"));
            }
            
            // 고객 ID 일치 검증 (추가 보안)
            if (headerCustomerId != null && !customerId.toString().equals(headerCustomerId)) {
                log.warn("Customer ID mismatch: path={}, header={}", customerId, headerCustomerId);
                return ResponseEntity.status(403).body(Map.of("error", "Customer ID mismatch"));
            }

            PointReservation reservation = pointReservationService.createReservation(
                    customerId, request.getOrderId(), request.getPoints());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "포인트 예약이 완료되었습니다",
                    "reservationId", reservation.getReservationId().toString(),
                    "customerId", customerId.toString(),
                    "orderId", request.getOrderId().toString(),
                    "points", request.getPoints()
            ));

        } catch (Exception e) {
            log.error("포인트 예약 실패: customerId={}, orderId={}", customerId, request.getOrderId(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "포인트 예약에 실패했습니다: " + e.getMessage()
            ));
        }
    }

    /**
     * 포인트 예약 처리 (실제 차감) - 오케스트레이션용
     */
    @PostMapping("/process-reservation")
    public ResponseEntity<Map<String, Object>> processReservation(
            @PathVariable UUID customerId,
            @RequestBody ProcessReservationRequest request,
            @RequestHeader(value = "X-Service-Name", required = false) String serviceName) {
        
        try {
            log.info("포인트 예약 처리 요청: customerId={}, orderId={}, serviceName={}", 
                    customerId, request.getOrderId(), serviceName);

            // 서비스 간 호출 검증
            if (!"order-service".equals(serviceName)) {
                log.warn("Unauthorized point processing attempt from: {}", serviceName);
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized service"));
            }

            pointReservationService.processReservation(request.getOrderId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "포인트 예약 처리가 완료되었습니다",
                    "customerId", customerId.toString(),
                    "orderId", request.getOrderId().toString()
            ));

        } catch (Exception e) {
            log.error("포인트 예약 처리 실패: customerId={}, orderId={}", customerId, request.getOrderId(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "포인트 예약 처리에 실패했습니다: " + e.getMessage()
            ));
        }
    }

    /**
     * 포인트 예약 취소 (보상 로직용)
     */
    @PostMapping("/cancel-reservation")
    public ResponseEntity<Map<String, Object>> cancelReservation(
            @PathVariable UUID customerId,
            @RequestBody CancelReservationRequest request,
            @RequestHeader(value = "X-Service-Name", required = false) String serviceName) {
        
        try {
            log.info("포인트 예약 취소 요청: customerId={}, orderId={}, serviceName={}", 
                    customerId, request.getOrderId(), serviceName);

            // 서비스 간 호출 검증
            if (!"order-service".equals(serviceName)) {
                log.warn("Unauthorized point cancellation attempt from: {}", serviceName);
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized service"));
            }

            pointReservationService.cancelReservation(request.getOrderId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "포인트 예약 취소가 완료되었습니다",
                    "customerId", customerId.toString(),
                    "orderId", request.getOrderId().toString()
            ));

        } catch (Exception e) {
            log.error("포인트 예약 취소 실패: customerId={}, orderId={}", customerId, request.getOrderId(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "포인트 예약 취소에 실패했습니다: " + e.getMessage()
            ));
        }
    }

    // DTO 클래스들
    public static class ReservePointsRequest {
        @NotNull
        private UUID orderId;
        @NotNull
        private Integer points;

        // getters and setters
        public UUID getOrderId() { return orderId; }
        public void setOrderId(UUID orderId) { this.orderId = orderId; }
        public Integer getPoints() { return points; }
        public void setPoints(Integer points) { this.points = points; }
    }

    public static class ProcessReservationRequest {
        @NotNull
        private UUID orderId;

        public UUID getOrderId() { return orderId; }
        public void setOrderId(UUID orderId) { this.orderId = orderId; }
    }

    public static class CancelReservationRequest {
        @NotNull
        private UUID orderId;

        public UUID getOrderId() { return orderId; }
        public void setOrderId(UUID orderId) { this.orderId = orderId; }
    }
}
