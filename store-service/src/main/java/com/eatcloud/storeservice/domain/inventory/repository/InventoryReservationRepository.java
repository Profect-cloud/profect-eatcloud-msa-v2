// InventoryReservationRepository.java
package com.eatcloud.storeservice.domain.inventory.repository;

import com.eatcloud.storeservice.domain.inventory.entity.InventoryReservation;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {

    Optional<InventoryReservation> findByOrderLineId(UUID orderLineId);

    List<InventoryReservation> findByStatusAndExpiresAtBefore(String status, LocalDateTime time, PageRequest pageRequest);
}
