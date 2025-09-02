package com.eatcloud.customerservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;

import io.lettuce.core.dynamic.annotation.Param;

import com.eatcloud.autotime.repository.SoftDeleteRepository;
import com.eatcloud.customerservice.entity.Address;

public interface AddressRepository extends SoftDeleteRepository<Address, UUID> {
	List<Address> findByCustomerIdAndDeletedAtIsNull(UUID customerId);

	Optional<Address> findByCustomerIdAndIsSelectedTrueAndDeletedAtIsNull(UUID customerId);

	@Query("SELECT a FROM Address a WHERE a.id = :id AND a.customer.id = :customerId AND a.deletedAt IS NULL")
	Optional<Address> findByIdAndCustomerId(@Param("id") UUID id, @Param("customerId") UUID customerId);
}
