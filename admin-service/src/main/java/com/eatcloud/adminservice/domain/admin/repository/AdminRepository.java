package com.eatcloud.adminservice.domain.admin.repository;


import com.eatcloud.adminservice.domain.admin.entity.Admin;
import com.eatcloud.autotime.repository.SoftDeleteRepository;
import com.eatcloud.logging.annotation.Loggable;

import java.util.Optional;
import java.util.UUID;

@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public interface AdminRepository extends SoftDeleteRepository<Admin, UUID> {
	Optional<Admin> findByEmail(String email);
}