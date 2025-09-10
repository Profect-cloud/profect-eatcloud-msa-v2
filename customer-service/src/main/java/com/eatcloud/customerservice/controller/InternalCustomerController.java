package com.eatcloud.customerservice.controller;

import com.eatcloud.customerservice.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 내부 서비스 간 통신을 위한 컨트롤러
 * 인증 없이 호출 가능
 */
@Slf4j
@RestController
@RequestMapping("/internal/api/v1/customers")
@RequiredArgsConstructor
public class InternalCustomerController {

    private final CustomerService customerService;

    @PostMapping("/{customerId}/points/reserve")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> reservePoints(
        @PathVariable UUID customerId,
        @RequestBody Map<String, Object> request) {

        log.info("Internal API: 포인트 예약 요청 - customerId: {}, request: {}", customerId, request);

        UUID orderId = UUID.fromString((String) request.get("orderId"));
        Integer points = (Integer) request.get("points");

        customerService.reservePoints(customerId, points);

        Map<String, String> response = Map.of(
            "reservationId", UUID.randomUUID().toString(),
            "message", "포인트가 성공적으로 예약되었습니다."
        );

        return response;
    }

    @PostMapping("/{customerId}/points/cancel-reservation")
    @ResponseStatus(HttpStatus.OK)
    public String cancelPointReservation(
        @PathVariable UUID customerId,
        @RequestBody Map<String, Object> request) {

        log.info("Internal API: 포인트 예약 취소 요청 - customerId: {}, request: {}", customerId, request);

        UUID orderId = UUID.fromString((String) request.get("orderId"));

        // 실제로는 orderId로 예약을 찾아서 취소해야 함
        // 현재는 단순히 고객의 예약 포인트를 취소
        customerService.cancelReservedPoints(customerId, 0); // 실제로는 orderId로 예약을 찾아야 함

        return "포인트 예약이 취소되었습니다.";
    }
}
