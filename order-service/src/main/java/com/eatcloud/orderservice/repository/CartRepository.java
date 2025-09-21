package com.eatcloud.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.eatcloud.logging.annotation.Loggable;
import com.eatcloud.orderservice.entity.Cart;
import com.eatcloud.autotime.repository.SoftDeleteRepository;

import java.util.Optional;
import java.util.UUID;


@Repository
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public interface CartRepository extends SoftDeleteRepository<Cart, UUID> {
    Optional<Cart> findByCustomerId(UUID customerId);
    void deleteByCustomerId(UUID customerId);

    @Query("SELECT COUNT(c) > 0 FROM Cart c WHERE c.customerId = :customerId")
    boolean existsByCustomerId(@Param("customerId") UUID customerId);
}
