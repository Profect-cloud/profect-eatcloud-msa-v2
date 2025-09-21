package com.eatcloud.managerservice.repository;

import java.util.Optional;
import java.util.UUID;

import com.eatcloud.autotime.repository.SoftDeleteRepository;
import com.eatcloud.logging.annotation.Loggable;
import com.eatcloud.managerservice.entity.Manager;

@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public interface ManagerRepository extends SoftDeleteRepository<Manager, UUID> {

	Optional<Manager> findByEmail(String email);

	Optional<Manager> findByIdAndDeletedAtIsNull(UUID id);
}

