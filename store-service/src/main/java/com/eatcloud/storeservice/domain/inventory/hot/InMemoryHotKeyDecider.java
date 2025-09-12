package com.eatcloud.storeservice.domain.inventory.hot;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// InMemoryHotKeyDecider
@Slf4j
@Component
@Primary // ← 여러 구현체가 있으면 이걸 기본으로 주입
@ConfigurationProperties(prefix = "inventory.hot")
public class InMemoryHotKeyDecider implements HotKeyDecider {

    @Setter private List<String> keys = List.of();   // yml 바인딩
    private final Set<UUID> hot = ConcurrentHashMap.newKeySet();

    @PostConstruct
    void init() {
        if (keys != null) {
            for (String k : keys) {
                try { hot.add(UUID.fromString(k)); }
                catch (Exception e) { log.warn("Invalid hot key UUID: {}", k); }
            }
        }
        log.info("[HotKey] loaded {} keys", hot.size());
    }

    @Override public boolean isHot(UUID menuId) { return hot.contains(menuId); }

    // 토글 구현
    @Override public void markHot(UUID id)    { hot.add(id); }
    @Override public void unmarkHot(UUID id)  { hot.remove(id); }
}
