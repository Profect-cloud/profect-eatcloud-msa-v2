package com.eatcloud.orderservice.repository;

import com.eatcloud.logging.annotation.Loggable;
import com.eatcloud.orderservice.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
} 