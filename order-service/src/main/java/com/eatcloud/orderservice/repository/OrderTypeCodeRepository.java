package com.eatcloud.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.eatcloud.orderservice.entity.OrderTypeCode;
import com.eatcloud.autotime.repository.SoftDeleteRepository;

import java.util.Optional;

@Repository
public interface OrderTypeCodeRepository extends SoftDeleteRepository<OrderTypeCode, String> {
    Optional<OrderTypeCode> findByCode(String code);
}
