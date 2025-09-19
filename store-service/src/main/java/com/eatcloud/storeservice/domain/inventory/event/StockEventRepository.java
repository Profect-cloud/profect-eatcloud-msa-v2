package com.eatcloud.storeservice.domain.inventory.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockEventRepository extends JpaRepository<StockEventEntity, UUID> {

    List<StockEventEntity> findByMenuIdOrderByCreatedAtAsc(UUID menuId);

    List<StockEventEntity> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    List<StockEventEntity> findByOrderLineIdOrderByCreatedAtAsc(UUID orderLineId);
}
