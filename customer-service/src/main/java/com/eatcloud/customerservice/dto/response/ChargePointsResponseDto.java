package com.eatcloud.customerservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargePointsResponseDto {
    
    private UUID customerId;
    private Integer chargedPoints;
    private Integer totalPoints;
    private Integer availablePoints;
    private LocalDateTime chargedAt;
    private String message;
}
