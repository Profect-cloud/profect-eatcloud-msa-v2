// com.eatcloud.storeservice.domain.outbox.service.OutboxCommandService.java
package com.eatcloud.storeservice.domain.outbox.service;

import com.eatcloud.storeservice.domain.outbox.entity.Outbox;
import com.eatcloud.storeservice.domain.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxCommandService {

    private final OutboxRepository repo;

    /** 배치 픽업: FOR UPDATE SKIP LOCKED → TX 필수 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Outbox> pickBatch(int limit) {
        return repo.pickBatchForPublish(limit);
    }

    /** 발행 성공 마킹 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(UUID id) {
        int updated = repo.markSent(id);
        // 필요 시 updated == 0 로깅
    }

    /** 실패 종결 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID id) {
        int updated = repo.markFailed(id);
        // 필요 시 updated == 0 로깅
    }

    /** 재시도 예약 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetry(UUID id, LocalDateTime next) {
        int updated = repo.markRetry(id, next);
        // 필요 시 updated == 0 로깅
    }
}
