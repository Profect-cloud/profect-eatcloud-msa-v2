// src/main/java/com/eatcloud/orderservice/read/ProjectionDebugController.java
package com.eatcloud.orderservice.read;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/internal/projection")
@RequiredArgsConstructor
public class ProjectionDebugController {
    private final OrderLineProjectionRepository repo;

    @GetMapping("/line/{lineId}")
    public Optional<OrderLineProjection> byLine(@PathVariable UUID lineId) {
        return repo.findById(lineId);
    }

    @GetMapping("/order/{orderId}")
    public List<OrderLineProjection> byOrder(@PathVariable UUID orderId) {
        // 간단히 전량 조회 후 필터 (규모 커지면 쿼리 메서드 추가)
        return repo.findAll().stream().filter(p -> p.getOrderId().equals(orderId)).toList();
    }
}
