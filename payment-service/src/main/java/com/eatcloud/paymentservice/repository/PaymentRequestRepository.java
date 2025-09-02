package com.eatcloud.paymentservice.repository;

import com.eatcloud.autotime.repository.SoftDeleteRepository;
import com.eatcloud.paymentservice.entity.PaymentRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRequestRepository extends SoftDeleteRepository<PaymentRequest, UUID> {
    Optional<PaymentRequest> findByOrderId(UUID orderId);
    List<PaymentRequest> findByStatusAndTimeoutAtBefore(String status, LocalDateTime timeoutAt);
} 