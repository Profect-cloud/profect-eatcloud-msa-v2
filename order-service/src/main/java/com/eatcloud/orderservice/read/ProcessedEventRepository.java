package com.eatcloud.orderservice.read;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProcessedEventRepository      extends JpaRepository<ProcessedEvent, UUID> {}
