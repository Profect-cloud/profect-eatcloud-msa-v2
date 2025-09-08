package com.eatcloud.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.eatcloud.logging.annotation.Loggable;
import com.eatcloud.orderservice.entity.OrderStatusCode;
import com.eatcloud.autotime.repository.SoftDeleteRepository;

import java.util.Optional;

@Repository
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public interface OrderStatusCodeRepository extends SoftDeleteRepository<OrderStatusCode, String> {
    Optional<OrderStatusCode> findByCode(String code);
}
