package com.eatcloud.storeservice.domain.inventory.service;

import com.eatcloud.storeservice.domain.inventory.event.StockEventEntity;
import com.eatcloud.storeservice.domain.inventory.event.StockEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockReplayService {

    private final StockEventRepository eventRepository;

    public int replayStock(UUID menuId) {
        List<StockEventEntity> events = eventRepository.findAll()
                .stream()
                .filter(e -> e.getMenuId().equals(menuId))
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .toList();

        int currentQty = 0;
        for (StockEventEntity e : events) {
            switch (e.getEventType()) {
                case "stock.reserved" -> currentQty -= e.getQuantity();
                case "stock.committed" -> {} // 확정 시 추가 변화 없음
                case "stock.released", "stock.returned", "stock.canceled" -> currentQty += e.getQuantity();
                case "stock.insufficient" -> {} // 부족은 상태 표시용
            }
        }
        return currentQty;
    }
}
