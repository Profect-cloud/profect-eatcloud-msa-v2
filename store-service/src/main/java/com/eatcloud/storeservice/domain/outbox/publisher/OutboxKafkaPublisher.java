package com.eatcloud.storeservice.domain.outbox.publisher;

import com.eatcloud.storeservice.domain.outbox.entity.Outbox;
import com.eatcloud.storeservice.domain.outbox.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxKafkaPublisher {

    private final OutboxRepository repo;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper om;

    @Value("${inventory.outbox.publisher.topic:stock-events}")
    private String topic;

    @Value("${inventory.outbox.publisher.batch-size:50}")
    private int batchSize;

    @Value("${inventory.outbox.publisher.max-retry:10}")
    private int maxRetry;

    @Value("${inventory.outbox.publisher.base-backoff-ms:30000}") // 30s
    private long baseBackoffMs;

    @Scheduled(fixedDelayString = "${inventory.outbox.publisher.interval-ms:5000}")
    public void publish() {
        List<Outbox> batch = pickBatch(batchSize);
        if (batch.isEmpty()) return;

        for (Outbox o : batch) {
            try {
                // body 직렬화
                String key = o.getAggregateId().toString(); // 파티션키(동일 aggregate 순서 보장)
                String value = om.writeValueAsString(Map.of(
                        "id",            o.getId(),
                        "eventType",     o.getEventType(),
                        "aggregateType", o.getAggregateType(),
                        "aggregateId",   o.getAggregateId(),
                        "payload",       o.getPayload(),
                        "createdAt",     o.getCreatedAt()
                ));

                ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);

                // 표준 헤더 (headers JSON에 correlationId 같은 게 있으면 같이 넣어줌)
                addHeader(record, "eventType",     o.getEventType());
                addHeader(record, "aggregateType", o.getAggregateType());
                addHeader(record, "aggregateId",   o.getAggregateId().toString());
                addHeader(record, "occurredAt",    o.getCreatedAt().toString());
                if (o.getHeaders() != null && o.getHeaders().has("correlationId")) {
                    addHeader(record, "correlationId", o.getHeaders().get("correlationId").asText());
                }
                addHeader(record, "producer", "store-service");

                // 동기 전송
                kafka.send(record).get();

                // 성공 마킹
                markSent(o.getId());
                log.info("✅ Kafka publish success: id={} type={} key={}", o.getId(), o.getEventType(), key);

            } catch (Exception e) {
                log.warn("❌ Kafka publish failed id={} type={} err={}", o.getId(), o.getEventType(), e.toString());
                onFailure(o);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected List<Outbox> pickBatch(int limit) {
        return repo.pickBatchForPublish(limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markSent(UUID id) {
        repo.markSent(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void onFailure(Outbox o) {
        int next = o.getRetryCount() + 1;
        if (next >= maxRetry) {
            repo.markFailed(o.getId());
            log.error("🟥 outbox moved to FAILED id={} retryCount={}", o.getId(), next);
            return;
        }
        long jitter = ThreadLocalRandom.current().nextLong(0, baseBackoffMs / 2);
        long delayMs = (long) (baseBackoffMs * Math.pow(2, Math.min(o.getRetryCount(), 5))) + jitter;
        LocalDateTime nextTime = LocalDateTime.now().plusNanos(delayMs * 1_000_000);
        repo.markRetry(o.getId(), nextTime);
    }

    private void addHeader(ProducerRecord<String, String> rec, String key, String val) {
        rec.headers().add(new RecordHeader(key, val.getBytes(StandardCharsets.UTF_8)));
    }
}
