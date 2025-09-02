package com.eatcloud.paymentservice.repository;

import com.eatcloud.autotime.repository.SoftDeleteRepository;
import com.eatcloud.paymentservice.entity.PaymentMethodCode;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentMethodCodeRepository extends SoftDeleteRepository<PaymentMethodCode, String> {
}