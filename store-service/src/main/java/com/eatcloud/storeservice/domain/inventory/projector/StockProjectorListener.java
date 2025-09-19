// package: com.eatcloud.storeservice.domain.inventory.projector
package com.eatcloud.storeservice.domain.inventory.projector;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockProjectorListener {

    private final ObjectMapper om;
    private final StockProjectorService projector;

    @Value("${inventory.topic:stock-events}")
    private String stockTopic;

    @KafkaListener(
            topics = "${inventory.topic:stock-events}",
            containerFactory = "stockStringKafkaListenerContainerFactory"
    )
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String raw = record.value();
        log.info("[Projector] RAW key={} value={}", record.key(), raw);

        try {
            if (raw == null || raw.isBlank()) {
                ack.acknowledge();
                return;
            }

            // 🔹 JsonSerializer 때문에 따옴표 + escape 붙는 경우 대비
            if (raw.startsWith("\"") && raw.endsWith("\"")) {
                raw = raw.substring(1, raw.length() - 1)  // 양쪽 따옴표 제거
                        .replace("\\\"", "\"")           // 이스케이프된 따옴표 복원
                        .replace("\\\\", "\\");          // 이스케이프된 역슬래시 복원
            }

            // 🔹 역직렬화 시도
            StockEventEnvelope evt = om.readValue(raw, StockEventEnvelope.class);
            log.info("[Projector] Parsed eventType={} aggregateId={}", evt.getEventType(), evt.getAggregateId());

            projector.apply(evt);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Projector] Consume failed: raw={} error={}", raw, e.getMessage(), e);
            // ack을 호출하면 offset은 넘기고, throw 하면 retry/DLT로 감
            ack.acknowledge();
        }
    }
}
