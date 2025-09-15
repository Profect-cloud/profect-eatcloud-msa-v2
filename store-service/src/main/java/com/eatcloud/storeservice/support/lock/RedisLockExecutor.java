package com.eatcloud.storeservice.support.lock;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class RedisLockExecutor {

    private final RedissonClient redisson;

    @Value("${inventory.lock.wait-ms:300}")
    private long waitMs;
    @Value("${inventory.lock.lease-ms:4000}")
    private long leaseMs;

    public <T> T withMenuLock(String menuKey, Supplier<T> body) {
        RLock lock = redisson.getLock("lock:menu:" + menuKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
            if (!locked) throw new LockTimeoutException("LOCK_TIMEOUT");
            return body.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockTimeoutException("INTERRUPTED", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    public static class LockTimeoutException extends RuntimeException {
        public LockTimeoutException(String m) { super(m); }
        public LockTimeoutException(String m, Throwable t) { super(m, t); }
    }
}

