// package: com.eatcloud.storeservice.domain.inventory.projector
package com.eatcloud.storeservice.domain.inventory.projector;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface StockProjectionRepository extends JpaRepository<StockProjectionEntity, UUID> { }
