package com.eatcloud.adminservice.domain.admin.repository;


import com.eatcloud.adminservice.domain.admin.entity.Admin;
import com.eatcloud.autotime.repository.SoftDeleteRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminRepository extends SoftDeleteRepository<Admin, UUID> {
	Optional<Admin> findByEmail(String email);
}