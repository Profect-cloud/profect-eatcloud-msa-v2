package com.eatcloud.orderservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentFailedRequest {
    
    private UUID paymentId;
    
    @NotBlank(message = "실패 사유는 필수입니다")
    private String failureReason;
    
    private String errorCode;
    private String paymentMethod;
}
