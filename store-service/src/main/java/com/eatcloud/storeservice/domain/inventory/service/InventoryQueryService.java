

package com.eatcloud.storeservice.domain.inventory.service;

import com.eatcloud.storeservice.domain.inventory.repository.InventoryStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryQueryService {

    private final InventoryStockRepository stockRepo;
    private final RedissonClient redisson;

    public StockView getStock(UUID menuId) {
        String availKey = "inv:" + menuId + ":avail";
        String reservedKey = "inv:" + menuId + ":reserved";

        var availBucket = redisson.getBucket(availKey, StringCodec.INSTANCE);
        var reservedBucket = redisson.getBucket(reservedKey, StringCodec.INSTANCE);

        String availStr = (String) availBucket.get();
        String reservedStr = (String) reservedBucket.get();

        if (availStr != null && reservedStr != null) {
            // Redis 캐시 히트
            return new StockView(Integer.parseInt(availStr), Integer.parseInt(reservedStr));
        }

        // Redis에 없으면 DB 조회
        var row = stockRepo.findById(menuId)
                .orElseThrow(() -> new IllegalArgumentException("NOT_FOUND"));

        int available = row.getAvailableQty();
        int reserved  = row.getReservedQty();

        // Redis에 보강(write-through)
        availBucket.set(String.valueOf(available));
        reservedBucket.set(String.valueOf(reserved));

        log.debug("[CACHE MISS] menuId={} → DB에서 조회 후 Redis 보강", menuId);
        return new StockView(available, reserved);
    }
}
