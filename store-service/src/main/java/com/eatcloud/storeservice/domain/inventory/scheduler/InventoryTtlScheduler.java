package com.eatcloud.storeservice.domain.inventory.scheduler;

import com.eatcloud.storeservice.domain.inventory.entity.InventoryReservation;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryReservationRepository;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryTtlScheduler {

    private final InventoryReservationRepository resRepo;
    private final InventoryStockRepository stockRepo;
    private final RedissonClient redisson;

    @Value("${inventory.ttl.enabled:true}")
    private boolean enabled;

    @Value("${inventory.ttl.batch-size:500}")
    private int batchSize;

    @Value("${inventory.ttl.lock.wait-ms:300}")
    private long waitMs;

    @Value("${inventory.ttl.lock.lease-ms:4000}")
    private long leaseMs;

    // 예: 30초마다 한 번 실행 (고정 지연)
    @Scheduled(fixedDelayString = "30000")
    public void run() {
        if (!enabled) return;

        LocalDateTime now = LocalDateTime.now();
        int processed = 0;

        try {
            while (processed < batchSize) {
                List<InventoryReservation> list = resRepo.findByStatusAndExpiresAtBefore(
                        "PENDING", now, PageRequest.of(0, Math.min(200, batchSize - processed))
                );
                if (list.isEmpty()) break;

                for (InventoryReservation r : list) {
                    try {
                        handleOneExpired(r);
                        processed++;
                    } catch (Exception ex) {
                        // 실패는 삼켜서 다음 건 진행 (알람/로깅)
                        log.warn("TTL cancel failed orderLineId={} menuId={} err={}",
                                r.getOrderLineId(), r.getMenuId(), ex.toString());
                    }
                    if (processed >= batchSize) break;
                }
            }
        } finally {
            if (processed > 0) {
                log.info("TTL scheduler processed count={}", processed);
            }
        }
    }

    /**
     * 개별 만료 예약 처리:
     * - (핫 메뉴 가정) RLock(menuId)
     * - 트랜잭션 내에서: PENDING -> CANCELED 멱등 전이 + 재고 복구(CAS)
     */
    protected void handleOneExpired(InventoryReservation r) {
        UUID menuId = r.getMenuId();
        String lockKey = "lock:menu:" + menuId;
        RLock lock = redisson.getLock(lockKey);

        boolean locked = false;
        try {
            locked = lock.tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
            if (!locked) {
                // 락 못 잡으면 스킵해서 다음 건 처리(다음 배치에 다시 시도)
                throw new RuntimeException("LOCK_TIMEOUT");
            }
            // 트랜잭션 경계는 짧게: 상태 전이 + CAS 복구만 포함
            cancelAndRestoreInTx(r);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while acquiring lock", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Transactional
    protected void cancelAndRestoreInTx(InventoryReservation r) {
        // 최신 상태 확인(동시에 다른 곳에서 처리됐을 수 있음)
        InventoryReservation cur = resRepo.findByOrderLineId(r.getOrderLineId())
                .orElse(null);
        if (cur == null) return; // 이미 정리됨
        if (!"PENDING".equals(cur.getStatus())) {
            return; // 이미 CONFIRMED/CANCELED 처리됨 → 멱등
        }

        // 1) 상태 전이: CANCELED
        cur.setStatus("CANCELED");
        cur.setReason("TTL_EXPIRED");
        resRepo.save(cur);

        // 2) 재고 복구(CAS) - is_unlimited=false 조건은 repo 쿼리 자체에 포함돼있다고 가정
        int updated = stockRepo.release(cur.getMenuId(), cur.getQty());
        if (updated == 0) {
            // 논리적으로는 거의 불가능하지만, 정책상 경고만 남기고 넘어간다(멱등 보장)
            log.warn("TTL restore CAS updated=0 menuId={} qty={}", cur.getMenuId(), cur.getQty());
        }

        // 3) (선택) 로그/아웃박스 기록
        // stockLogRepo.save( ... action=CANCEL, change=+qty, reason=TTL_EXPIRED ...)
        // outboxRepo.save( ... event='stock.released' ... )
    }
}
