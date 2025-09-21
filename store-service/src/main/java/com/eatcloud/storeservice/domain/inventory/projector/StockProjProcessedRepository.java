package com.eatcloud.storeservice.domain.inventory.projector;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface StockProjProcessedRepository extends JpaRepository<StockProjProcessed, UUID> { }
