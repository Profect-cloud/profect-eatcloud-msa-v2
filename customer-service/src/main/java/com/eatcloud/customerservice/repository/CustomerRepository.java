package com.eatcloud.customerservice.repository;

import java.util.Optional;
import java.util.UUID;

import com.eatcloud.autotime.repository.SoftDeleteRepository;
import com.eatcloud.customerservice.entity.Customer;

public interface CustomerRepository extends SoftDeleteRepository<Customer, UUID> {
	Optional<Customer> findByEmail(String email);

	Optional<Customer> findByNameAndDeletedAtIsNull(String name);
	Optional<Customer> findByEmailAndDeletedAtIsNull(String email);
	boolean existsByNameAndDeletedAtIsNull(String name);
	boolean existsByEmailAndDeletedAtIsNull(String email);
	boolean existsByNicknameAndDeletedAtIsNull(String nickname);
}
