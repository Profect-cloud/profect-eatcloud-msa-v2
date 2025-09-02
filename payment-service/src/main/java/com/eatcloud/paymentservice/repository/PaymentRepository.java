package com.eatcloud.paymentservice.repository;

import com.eatcloud.autotime.repository.SoftDeleteRepository;
import com.eatcloud.paymentservice.entity.Payment;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends SoftDeleteRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);
} 