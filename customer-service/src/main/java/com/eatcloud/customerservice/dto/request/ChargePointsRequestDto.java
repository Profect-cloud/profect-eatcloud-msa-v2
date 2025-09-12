package com.eatcloud.customerservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargePointsRequestDto {
    
    @NotNull(message = "충전할 포인트는 필수입니다.")
    @Min(value = 1, message = "충전할 포인트는 1 이상이어야 합니다.")
    private Integer points;
    
    private String description; // 충전 사유 (선택사항)
}
