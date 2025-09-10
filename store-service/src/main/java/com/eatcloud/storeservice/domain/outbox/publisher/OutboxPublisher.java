package com.eatcloud.storeservice.domain.outbox.publisher;

import com.eatcloud.storeservice.domain.outbox.entity.Outbox;
import com.eatcloud.storeservice.domain.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository repo;

    @Value("${inventory.outbox.publisher.enabled:true}")
    private boolean enabled;

    @Value("${inventory.outbox.publisher.batch-size:200}")
    private int batchSize;

    @Value("${inventory.outbox.publisher.webhook-url:http://localhost:18080/mock}")
    private String webhookUrl;

    private final WebClient webClient = WebClient.builder().build();

    @Scheduled(fixedDelayString = "5000")
    @Transactional
    public void publish() {
        if (!enabled) return;

        List<Outbox> list = repo.findUnpublished(PageRequest.of(0, batchSize));
        for (Outbox o : list) {
            try {
                webClient.post()
                        .uri(webhookUrl)
                        .bodyValue(o.getPayload())
                        .retrieve()
                        .toBodilessEntity()
                        .block();

                int updated = repo.markPublished(o.getId(), LocalDateTime.now());
                if (updated == 0) {
                    log.info("Outbox already published id={}", o.getId());
                }
            } catch (Exception ex) {
                log.warn("Outbox publish failed id={} err={}", o.getId(), ex.toString());
                // 미발행이면 다음 주기 재시도 → at-least-once
            }
        }
    }
}
