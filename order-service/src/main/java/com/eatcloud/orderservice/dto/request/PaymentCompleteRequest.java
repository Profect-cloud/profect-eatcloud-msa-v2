package com.eatcloud.orderservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentCompleteRequest {
    
    @NotNull(message = "결제 ID는 필수입니다")
    private UUID paymentId;
    
    private String paymentMethod;
    private String transactionId;
    private Integer paidAmount;
}
