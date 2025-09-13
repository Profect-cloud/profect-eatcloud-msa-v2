package com.eatcloud.customerservice.repository;

import com.eatcloud.customerservice.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    boolean existsByEventTypeAndOrderId(String eventType, UUID orderId);
}


