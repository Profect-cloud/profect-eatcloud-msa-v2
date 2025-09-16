package com.eatcloud.storeservice.domain.outbox.repository;

import com.eatcloud.storeservice.domain.outbox.entity.Outbox;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {

    @Query("""
       select o from Outbox o
        where o.sent = false
        order by o.createdAt asc
    """)
    List<Outbox> findUnsent(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Outbox o set o.sent = true where o.id = :id and o.sent = false")
    int markSent(@Param("id") UUID id);

    // 파생 쿼리 버전이 필요하면 둘 중 하나만 두세요
    List<Outbox> findTop50BySentFalseOrderByCreatedAtAsc();
}

