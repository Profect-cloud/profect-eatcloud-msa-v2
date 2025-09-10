package com.eatcloud.storeservice.domain.inventory.service;

import com.eatcloud.storeservice.domain.inventory.entity.InventoryStock;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryQueryService {

    private final InventoryStockRepository stockRepo;
    private final StringRedisTemplate redis;

    @Value("${inventory.cache.ttl-seconds:300}")
    private long ttlSeconds;

    public InventoryView getStock(UUID menuId) {
        String key = "stock:" + menuId;
        var v = redis.opsForHash().entries(key);
        if (!v.isEmpty()) {
            int available = Integer.parseInt((String) v.getOrDefault("available", "0"));
            int reserved  = Integer.parseInt((String) v.getOrDefault("reserved", "0"));
            return new InventoryView(available, reserved);
        }
        // miss → DB
        Optional<InventoryStock> opt = stockRepo.findById(menuId);
        int available = opt.map(InventoryStock::getAvailableQty).orElse(0);
        int reserved  = opt.map(InventoryStock::getReservedQty).orElse(0);
        // fill
        redis.opsForHash().put(key, "available", String.valueOf(available));
        redis.opsForHash().put(key, "reserved", String.valueOf(reserved));
        redis.expire(key, Duration.ofSeconds(ttlSeconds));
        return new InventoryView(available, reserved);
    }

    public record InventoryView(int available, int reserved) {}
}
