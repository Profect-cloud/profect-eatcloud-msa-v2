package com.eatcloud.orderservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.eatcloud.orderservice.entity.Order;
import com.eatcloud.autotime.repository.SoftDeleteRepository;

@Repository
public interface OrderRepository extends SoftDeleteRepository<Order, UUID> {
    @Query("SELECT o FROM Order o WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumber(@Param("orderNumber") String orderNumber);

    @Query("SELECT o FROM Order o WHERE o.orderId = :orderId AND o.customerId = :customerId AND o.deletedAt IS NULL")
    Optional<Order> findByOrderIdAndCustomerIdAndDeletedAtIsNull(
        @Param("orderId") UUID orderId,
        @Param("customerId") UUID customerId
    );

    List<Order> findAllByCustomerId(UUID customerId);

    List<Order> findAllByStoreId(UUID storeId);

    Optional<Order> findByOrderIdAndStoreId(UUID orderId, UUID storeId);
}
