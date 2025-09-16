package com.eatcloud.storeservice.domain.outbox.repository;

import com.eatcloud.storeservice.domain.outbox.entity.Outbox;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {

    // 배치 픽업: 상태 PENDING & 재시도 만기, 경쟁 회피
    @Query(value = """
        SELECT * FROM p_outbox
         WHERE status = 'PENDING'
           AND (next_attempt_at IS NULL OR next_attempt_at <= now())
         ORDER BY created_at ASC
         LIMIT :limit
         FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Outbox> pickBatchForPublish(@Param("limit") int limit);

    // 발행 성공 마킹
    @Modifying
    @Query("""
      UPDATE Outbox o
         SET o.status = 'SENT',
             o.publishedAt = CURRENT_TIMESTAMP,
             o.retryCount = o.retryCount + 1,
             o.sent = true
       WHERE o.id = :id
    """)
    int markSent(@Param("id") UUID id);

    // 재시도 예약
    @Modifying
    @Query("""
      UPDATE Outbox o
         SET o.status = 'PENDING',
             o.retryCount = o.retryCount + 1,
             o.nextAttemptAt = :next
       WHERE o.id = :id
    """)
    int markRetry(@Param("id") UUID id, @Param("next") LocalDateTime next);

    // 실패 종결
    @Modifying
    @Query("""
      UPDATE Outbox o
         SET o.status = 'FAILED',
             o.retryCount = o.retryCount + 1,
             o.nextAttemptAt = NULL
       WHERE o.id = :id
    """)
    int markFailed(@Param("id") UUID id);

    // (레거시) 필요하면 잠시 유지 가능
    // List<Outbox> findTop50BySentFalseOrderByCreatedAtAsc();
}
