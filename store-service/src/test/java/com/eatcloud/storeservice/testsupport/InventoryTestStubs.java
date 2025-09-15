package com.eatcloud.storeservice.testsupport;

import com.eatcloud.storeservice.support.lock.RedisLockExecutor;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@TestConfiguration
public class InventoryTestStubs {

    /** 실 Redis 대신 JVM ReentrantLock으로 직렬화하는 스텁 */
    public static class LockStub extends RedisLockExecutor {
        private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
        private volatile boolean forceTimeout = false;

        public LockStub() {
            // 현재 RedisLockExecutor 생성자는 (RedissonClient) 1개짜리입니다.
            super((RedissonClient) null);
        }

        public void setForceTimeout(boolean on) { this.forceTimeout = on; }

        @Override
        public <T> T withMenuLock(String key, Supplier<T> body) {
            if (forceTimeout) {
                throw new LockTimeoutException("forced-timeout");
            }
            final ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
            boolean ok = false;
            try {
                ok = lock.tryLock(5, TimeUnit.SECONDS);
                if (!ok) throw new LockTimeoutException("tryLock-timeout");
                return body.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LockTimeoutException("interrupted while acquiring local test lock");
            } finally {
                if (ok) lock.unlock();
            }
        }
    }

    @Bean @Primary
    public RedisLockExecutor redisLockExecutor() {
        return new LockStub();
    }
}
