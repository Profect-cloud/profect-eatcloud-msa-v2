package com.eatcloud.orderservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "outbox")
public class OutboxMappingProperties {
    private Map<String, String> mapping = new HashMap<>();

    public Map<String, String> getMapping() {
        return mapping;
    }

    public void setMapping(Map<String, String> mapping) {
        this.mapping = mapping;
    }

    public String resolveTopic(String eventType) {
        String direct = mapping.get(eventType);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        String lower = mapping.get(eventType.toLowerCase());
        if (lower != null && !lower.isBlank()) {
            return lower;
        }
        String kebab = mapping.get(toKebabCase(eventType));
        if (kebab != null && !kebab.isBlank()) {
            return kebab;
        }
        return null;
    }

    private String toKebabCase(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('-');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}


