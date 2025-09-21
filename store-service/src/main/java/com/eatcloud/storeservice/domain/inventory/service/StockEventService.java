package com.eatcloud.storeservice.domain.inventory.service;

import com.eatcloud.storeservice.domain.inventory.event.StockEventEntity;
import com.eatcloud.storeservice.domain.inventory.event.StockEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockEventService {

    private final StockEventRepository eventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public void recordAndPublishEvent(UUID menuId, UUID orderId, UUID orderLineId,
                                      String type, int qty, String reason) {

        var evt = StockEventEntity.builder()
                .menuId(menuId)
                .orderId(orderId)
                .orderLineId(orderLineId)
                .eventType(type)
                .quantity(qty)
                .reason(reason)
                .build();

        eventRepository.save(evt);

        try {
            kafkaTemplate.send("stock-events", orderLineId.toString(), evt);
            log.info("✅ stock-event published: type={}, orderLineId={}", type, orderLineId);
        } catch (Exception e) {
            log.error("❌ stock-event publish failed: {}", e.getMessage(), e);
            // Outbox 패턴 고려 가능
        }
    }

    @Transactional
    public void recordOnly(UUID menuId, UUID orderId, UUID orderLineId,
                           String type, int qty, String reason) {
        var evt = StockEventEntity.builder()
                .menuId(menuId)
                .orderId(orderId)
                .orderLineId(orderLineId)
                .eventType(type)
                .quantity(qty)
                .reason(reason)
                .build();
        eventRepository.save(evt);  // ✅ 저장만, 카프카 발행 없음
    }
}
