package com.eatcloud.storeservice.domain.outbox.repository;

import com.eatcloud.storeservice.domain.outbox.entity.Outbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {

    @Query("""
      SELECT o FROM Outbox o
       WHERE o.publishedAt IS NULL
       ORDER BY o.createdAt ASC
    """)
    List<Outbox> findUnpublished(Pageable pageable);

    @Modifying
    @Query("UPDATE Outbox o SET o.publishedAt = :now WHERE o.id = :id AND o.publishedAt IS NULL")
    int markPublished(@Param("id") UUID id, @Param("now") LocalDateTime now);
}

