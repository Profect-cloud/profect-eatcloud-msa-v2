package com.eatcloud.orderservice.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PaymentCompletedEvent {
    
    private UUID paymentId;
    private UUID orderId;
    private UUID customerId;
    private Integer amount;
    private String paymentMethod;
    private String pgTransactionId;
    private LocalDateTime completedAt;
    
    // 결제 후 처리에 필요한 추가 정보
    private String paymentKey;  // 토스 결제키
    private String approvalCode; // 승인번호
    
    // 주문 관련 정보 (비동기 후처리용)
    private Integer pointsUsed;   // 결제에 사용된 포인트
}
