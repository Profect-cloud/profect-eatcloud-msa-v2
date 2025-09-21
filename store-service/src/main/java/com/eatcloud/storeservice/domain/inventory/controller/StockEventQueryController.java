package com.eatcloud.storeservice.domain.inventory.controller;

import com.eatcloud.storeservice.domain.inventory.event.StockEventEntity;
import com.eatcloud.storeservice.domain.inventory.event.StockEventRepository;
import com.eatcloud.storeservice.domain.inventory.service.StockReplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/internal/stock")
@RequiredArgsConstructor
public class StockEventQueryController {

    private final StockEventRepository repo;
    private final StockReplayService replayService;

    /** 메뉴 타임라인 조회 */
    @GetMapping("/events")
    public List<StockEventEntity> getEvents(
            @RequestParam(required = false) UUID menuId,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) UUID orderLineId
    ) {
        if (menuId != null) return repo.findByMenuIdOrderByCreatedAtAsc(menuId);
        if (orderId != null) return repo.findByOrderIdOrderByCreatedAtAsc(orderId);
        if (orderLineId != null) return repo.findByOrderLineIdOrderByCreatedAtAsc(orderLineId);
        // 기본: 최근 100건
        return repo.findAll().stream()
                .sorted(Comparator.comparing(StockEventEntity::getCreatedAt).reversed())
                .limit(100)
                .toList();
    }

    /** 메뉴 단일 리플레이 (가용수량 추정) */
    @GetMapping("/replay/{menuId}")
    public Map<String, Object> replay(@PathVariable UUID menuId) {
        int available = replayService.replayStock(menuId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("menuId", menuId);
        result.put("replayedAvailable", available);
        result.put("eventsCount", repo.findByMenuIdOrderByCreatedAtAsc(menuId).size());
        return result;
    }
}
