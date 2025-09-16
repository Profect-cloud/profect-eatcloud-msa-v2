package com.eatcloud.orderservice.stock;

import com.eatcloud.orderservice.read.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final ObjectMapper om;
    private final OrderLineProjectionRepository projectionRepo;
    private final ProcessedEventRepository processedRepo;

    @KafkaListener(
            topics = "${inventory.topic:stock-events}",
            containerFactory = "stockStringKafkaListenerContainerFactory"
    )
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String raw = unwrapIfQuotedJson(record.value());
            if (raw == null || raw.isBlank()) {
                log.warn("stock-event skip: empty payload (topic={}, partition={}, offset={})",
                        record.topic(), record.partition(), record.offset());
                ack.acknowledge();
                return;
            }

            StockEventEnvelope evt = om.readValue(raw, StockEventEnvelope.class);
            if (evt.getId() == null) {
                log.warn("stock-event skip: no id (payload={})", raw);
                ack.acknowledge();
                return;
            }

            // 멱등 처리
            if (processedRepo.existsById(evt.getId())) {
                ack.acknowledge();
                return;
            }

            var p = evt.getPayload();
            if (p == null ||
                    !p.hasNonNull("orderLineId") ||
                    !p.hasNonNull("orderId") ||
                    !p.hasNonNull("menuId")) {
                log.warn("stock-event skip: missing required fields (payload={})", raw);
                ack.acknowledge();
                return;
            }

            UUID lineId = UUID.fromString(p.get("orderLineId").asText());
            UUID orderId = UUID.fromString(p.get("orderId").asText());
            UUID menuId  = UUID.fromString(p.get("menuId").asText());
            int qty = p.has("qty") ? p.get("qty").asInt() : 0;

            OrderLineProjection view = projectionRepo.findById(lineId).orElse(
                    OrderLineProjection.builder()
                            .orderLineId(lineId)
                            .orderId(orderId)
                            .menuId(menuId)
                            .qty(qty)
                            .build()
            );

            switch (evt.getEventType()) {
                case "stock.reserved"     -> view.setStockStatus("RESERVED");
                case "stock.committed"    -> view.setStockStatus("COMMITTED");
                case "stock.released"     -> view.setStockStatus("RELEASED");
                case "stock.insufficient" -> view.setStockStatus("INSUFFICIENT");
                default -> {
                    log.debug("stock-event ignore: unknown type={} lineId={}", evt.getEventType(), lineId);
                    ack.acknowledge();
                    return;
                }
            }

            if (qty > 0) view.setQty(qty);
            view.setUpdatedAt(LocalDateTime.now());
            projectionRepo.save(view);

            processedRepo.save(
                    ProcessedEvent.builder()
                            .eventId(evt.getId())
                            .processedAt(LocalDateTime.now())
                            .build()
            );

            ack.acknowledge();
            log.info("stock-event processed: type={} lineId={}", evt.getEventType(), lineId);
        } catch (Exception e) {
            log.error("stock-event consume fail (will retry/DLT): {}", e.toString());
            // 재시도/ DLT 로 넘기기 위해 예외 재던짐
            throw new RuntimeException(e);
        }
    }

    /** JsonSerializer로 이스케이프되어 온 "\"{...}\"" 형태를 정상 JSON으로 복원 */
    private String unwrapIfQuotedJson(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            raw = raw.substring(1, raw.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return raw;
    }
}
