package com.eatcloud.storeservice.domain.outbox.publisher;

import com.eatcloud.storeservice.domain.outbox.entity.Outbox;
import com.eatcloud.storeservice.domain.outbox.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxKafkaPublisher {

    private final OutboxRepository repo;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper om;

    @Value("${inventory.outbox.publisher.topic:stock-events}")
    private String topic;

    @Scheduled(fixedDelayString = "${inventory.outbox.publisher.interval-ms:5000}")
    @Transactional
    public void publish() {
        var batch = repo.findTop50BySentFalseOrderByCreatedAtAsc();
        for (Outbox o : batch) {
            try {
                // 1) 메시지 생성
                String key = o.getAggregateId().toString();
                String value = om.writeValueAsString(Map.of(
                        "id",         o.getId(),
                        "type",       o.getEventType(),
                        "aggregateId",o.getAggregateId(),
                        "payload",    o.getPayload(),      // JsonNode 그대로 직렬화 OK
                        "createdAt",  o.getCreatedAt()
                ));

                // 2) 동기 전송(성공/실패 판단)
                kafka.send(topic, key, value).get();

                // 3) 원자적 마킹(중복 방지)
                int updated = repo.markSent(o.getId());
                if (updated == 0) {
                    // 다른 워커/스레드가 먼저 처리했을 수도 있음
                    log.debug("Already marked sent. id={}", o.getId());
                } else {
                    log.info("✅ Kafka publish success: id={} type={}", o.getId(), o.getEventType());
                }

            } catch (Exception e) {
                // 보류: 다음 주기에 재시도 → at-least-once
                log.warn("❌ Kafka publish failed id={} err={}", o.getId(), e.toString());
            }
        }
    }
}
