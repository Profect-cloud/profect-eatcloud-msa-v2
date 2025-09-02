package com.eatcloud.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 분산락 서비스
 * 장바구니→주문 변환 시 동시성 제어를 위해 사용
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLockService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String LOCK_PREFIX = "lock:";
    
    // Lua script for atomic lock release
    private static final String UNLOCK_SCRIPT = 
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "    return redis.call('del', KEYS[1]) " +
        "else " +
        "    return 0 " +
        "end";
    
    /**
     * 분산락 획득 시도
     * @param key 락 키
     * @param timeoutSeconds 타임아웃 (초)
     * @return 락 획득 성공 여부
     */
    public boolean tryLock(String key, long timeoutSeconds) {
        String lockKey = LOCK_PREFIX + key;
        String lockValue = Thread.currentThread().getId() + ":" + System.nanoTime();
        
        try {
            Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, timeoutSeconds, TimeUnit.SECONDS);
            
            if (Boolean.TRUE.equals(result)) {
                log.debug("Lock acquired: key={}, thread={}", lockKey, Thread.currentThread().getId());
                return true;
            }
            
            log.debug("Failed to acquire lock: key={}, thread={}", lockKey, Thread.currentThread().getId());
            return false;
            
        } catch (Exception e) {
            log.error("Error acquiring lock: key={}", lockKey, e);
            return false;
        }
    }
    
    /**
     * 분산락 해제
     * @param key 락 키
     */
    public void unlock(String key) {
        String lockKey = LOCK_PREFIX + key;
        String lockValue = Thread.currentThread().getId() + ":" + System.nanoTime();
        
        try {
            // Lua script로 atomic하게 처리
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptText(UNLOCK_SCRIPT);
            redisScript.setResultType(Long.class);
            
            Long result = redisTemplate.execute(
                redisScript,
                Collections.singletonList(lockKey),
                lockValue
            );
            
            if (result != null && result > 0) {
                log.debug("Lock released: key={}, thread={}", lockKey, Thread.currentThread().getId());
            }
        } catch (Exception e) {
            log.error("Error releasing lock: key={}", lockKey, e);
        }
    }
}
