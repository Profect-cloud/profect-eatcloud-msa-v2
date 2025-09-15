package com.eatcloud.storeservice.internal.lock;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class MenuLock {

    private final RedissonClient redisson;

    @Value("${inventory.lock.wait-millis:300}")  long waitMs;
    @Value("${inventory.lock.lease-millis:4000}") long leaseMs;
    @Value("${inventory.lock.retry-count:3}")     int retry;

    public void withLock(UUID menuId, Runnable critical) {
        RLock lock = redisson.getLock("lock:menu:" + menuId);
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                boolean ok = lock.tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
                if (!ok) {
                    if (attempt > retry) throw new RuntimeException("LOCK_TIMEOUT");
                    Thread.sleep(Math.min(50L << (attempt - 1), 300)); // 50/100/200ms
                    continue;
                }
                try { critical.run(); return; }
                finally { if (lock.isHeldByCurrentThread()) lock.unlock(); }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}
