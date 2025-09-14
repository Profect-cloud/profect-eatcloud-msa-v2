package com.eatcloud.orderservice.repository;

import com.eatcloud.orderservice.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
            List<OutboxEvent.Status> statuses,
            LocalDateTime nextAttemptAt,
            Pageable pageable
    );
}


