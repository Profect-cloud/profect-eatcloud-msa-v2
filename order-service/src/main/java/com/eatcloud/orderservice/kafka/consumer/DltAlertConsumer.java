package com.eatcloud.orderservice.kafka.consumer;

import com.eatcloud.orderservice.alert.DiscordNotifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class DltAlertConsumer {

    private final DiscordNotifier discordNotifier;
    private final ObjectMapper objectMapper;

    @Value("${alert.dlt.enabled:true}")
    private boolean enabled;

    @KafkaListener(topicPattern = ".*[.-]dlt$", groupId = "order-service-dlt", containerFactory = "dltAlertKafkaListenerContainerFactory")
    public void onAnyDlt(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("DLT 수신: topic={}, partition={}, offset={}", record.topic(), record.partition(), record.offset());
        handle(record.topic(), record, ack);
    }

    private void handle(String topic, ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            if (!enabled) {
                ack.acknowledge();
                return;
            }
            String formattedTimestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(record.timestamp()));

            String prettyPayload = prettyJsonOrRaw(record.value(), 1600);

            StringBuilder sb = new StringBuilder();
            sb.append(":rotating_light: DLT 수신\n")
              .append("- topic: ").append(topic).append("\n")
              .append("- key: ").append(record.key()).append("\n")
              .append("- partition: ").append(record.partition()).append(", offset: ").append(record.offset()).append("\n")
              .append("- timestamp: ").append(formattedTimestamp).append("\n")
              .append("- payload:\n")
              .append("```json\n")
              .append(prettyPayload)
              .append("\n```");

            String msg = sb.toString();
            discordNotifier.sendText(msg);
        } catch (Exception e) {
            log.warn("DLT alert send failed: topic={}, offset={}", topic, record.offset(), e);
        } finally {
            ack.acknowledge();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private String prettyJsonOrRaw(String raw, int maxChars) {
        try {
            if (raw == null) return null;
            JsonNode node = objectMapper.readTree(raw);
            String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            return truncate(pretty, maxChars);
        } catch (Exception ignore) {
            return truncate(raw, maxChars);
        }
    }
}


