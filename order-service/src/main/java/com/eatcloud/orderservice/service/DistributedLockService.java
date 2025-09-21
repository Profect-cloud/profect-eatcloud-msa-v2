package com.eatcloud.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.eatcloud.logging.annotation.Loggable;

@Service
@RequiredArgsConstructor
@Slf4j
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public class DistributedLockService {
    
    private final RedissonClient redissonClient;
    private static final String LOCK_PREFIX = "lock:";
    

    public <T> T executeWithLock(String key, long waitTime, long leaseTime, TimeUnit unit, 
                                  Callable<T> task) throws Exception {
        RLock lock = redissonClient.getFairLock(LOCK_PREFIX + key);
        boolean acquired = false;
        
        try {
            acquired = lock.tryLock(waitTime, leaseTime, unit);
            
            if (!acquired) {
                log.warn("Failed to acquire lock for key: {}, thread: {}", key, Thread.currentThread().getId());
                throw new RuntimeException("Failed to acquire lock for key: " + key);
            }
            
            log.debug("Lock acquired: key={}, thread={}", key, Thread.currentThread().getId());
            return task.call();
            
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released: key={}, thread={}", key, Thread.currentThread().getId());
            }
        }
    }

    public boolean tryLock(String key, long leaseTime, TimeUnit unit) {
        RLock lock = redissonClient.getFairLock(LOCK_PREFIX + key);
        
        try {
            boolean acquired = lock.tryLock(0, leaseTime, unit);
            if (acquired) {
                log.debug("Lock acquired immediately: key={}, thread={}", key, Thread.currentThread().getId());
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted: key={}", key, e);
            return false;
        }
    }

    public void unlock(String key) {
        RLock lock = redissonClient.getFairLock(LOCK_PREFIX + key);
        
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Lock released: key={}, thread={}", key, Thread.currentThread().getId());
        } else {
            log.warn("Attempted to unlock non-owned lock: key={}, thread={}", key, Thread.currentThread().getId());
        }
    }

    public <T> T executeWithMultiLock(String[] keys, long waitTime, long leaseTime, TimeUnit unit,
                                       Callable<T> task) throws Exception {
        RLock[] locks = new RLock[keys.length];
        for (int i = 0; i < keys.length; i++) {
            locks[i] = redissonClient.getFairLock(LOCK_PREFIX + keys[i]);
        }
        
        RLock multiLock = redissonClient.getMultiLock(locks);
        boolean acquired = false;
        
        try {
            acquired = multiLock.tryLock(waitTime, leaseTime, unit);
            
            if (!acquired) {
                log.warn("Failed to acquire multi-lock for keys: {}, thread: {}", 
                        String.join(", ", keys), Thread.currentThread().getId());
                throw new RuntimeException("Failed to acquire multi-lock for keys: " + String.join(", ", keys));
            }
            
            log.debug("Multi-lock acquired: keys={}, thread={}", 
                     String.join(", ", keys), Thread.currentThread().getId());
            return task.call();
            
        } finally {
            if (acquired && multiLock.isHeldByCurrentThread()) {
                multiLock.unlock();
                log.debug("Multi-lock released: keys={}, thread={}", 
                         String.join(", ", keys), Thread.currentThread().getId());
            }
        }
    }

    public <T> T executeWithReadLock(String key, long waitTime, long leaseTime, TimeUnit unit,
                                      Callable<T> task) throws Exception {
        RReadWriteLock rwLock = redissonClient.getReadWriteLock(LOCK_PREFIX + key);
        RLock readLock = rwLock.readLock();
        boolean acquired = false;
        
        try {
            acquired = readLock.tryLock(waitTime, leaseTime, unit);
            
            if (!acquired) {
                throw new RuntimeException("Failed to acquire read lock for key: " + key);
            }
            
            log.debug("Read lock acquired: key={}, thread={}", key, Thread.currentThread().getId());
            return task.call();
            
        } finally {
            if (acquired && readLock.isHeldByCurrentThread()) {
                readLock.unlock();
                log.debug("Read lock released: key={}, thread={}", key, Thread.currentThread().getId());
            }
        }
    }

    public <T> T executeWithWriteLock(String key, long waitTime, long leaseTime, TimeUnit unit,
                                       Callable<T> task) throws Exception {
        RReadWriteLock rwLock = redissonClient.getReadWriteLock(LOCK_PREFIX + key);
        RLock writeLock = rwLock.writeLock();
        boolean acquired = false;
        
        try {
            acquired = writeLock.tryLock(waitTime, leaseTime, unit);
            
            if (!acquired) {
                throw new RuntimeException("Failed to acquire write lock for key: " + key);
            }
            
            log.debug("Write lock acquired: key={}, thread={}", key, Thread.currentThread().getId());
            return task.call();
            
        } finally {
            if (acquired && writeLock.isHeldByCurrentThread()) {
                writeLock.unlock();
                log.debug("Write lock released: key={}, thread={}", key, Thread.currentThread().getId());
            }
        }
    }

    public boolean isLocked(String key) {
        RLock lock = redissonClient.getFairLock(LOCK_PREFIX + key);
        return lock.isLocked();
    }

    public boolean isHeldByCurrentThread(String key) {
        RLock lock = redissonClient.getFairLock(LOCK_PREFIX + key);
        return lock.isHeldByCurrentThread();
    }
}
