package com.eatcloud.orderservice.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordNotifier {

    @Value("${alert.discord.enabled:false}")
    private boolean enabled;

    @Value("${alert.discord.webhook-url:}")
    private String webhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendText(String text) {
        if (!enabled || webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("Discord disabled or webhook not set. message={}", text);
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> payload = new HashMap<>();
            payload.put("content", text);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(webhookUrl, entity, String.class);
        } catch (Exception e) {
            log.warn("Discord notify failed: {}", e.getMessage());
        }
    }
}


