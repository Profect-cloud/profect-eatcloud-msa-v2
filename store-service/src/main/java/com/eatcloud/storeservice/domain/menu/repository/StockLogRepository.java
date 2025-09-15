package com.eatcloud.storeservice.domain.menu.repository;

import com.eatcloud.storeservice.domain.menu.entity.StockLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockLogRepository extends JpaRepository<StockLog, UUID> {}
