package com.eatcloud.storeservice.domain.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 매장 상세 정보 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreDetailResponseDto {
    
    /**
     * 매장 ID
     */
    private Long storeId;
    
    /**
     * 매장명
     */
    private String storeName;
    
    /**
     * 카테고리
     */
    private String category;
    
    /**
     * 매장 설명
     */
    private String description;
    
    /**
     * 매장 주소
     */
    private String address;
    
    /**
     * 매장 전화번호
     */
    private String phoneNumber;
    
    /**
     * 영업 시간
     */
    private String businessHours;
    
    /**
     * 평점
     */
    private BigDecimal rating;
    
    /**
     * 리뷰 수
     */
    private Integer reviewCount;
    
    /**
     * 최소 주문 금액
     */
    private Integer minimumOrderAmount;
    
    /**
     * 배달비
     */
    private Integer deliveryFee;
    
    /**
     * 매장 이미지 URL
     */
    private String imageUrl;
    
    /**
     * 배달 가능 여부
     */
    private Boolean deliveryAvailable;
    
    /**
     * 포장 가능 여부
     */
    private Boolean takeoutAvailable;
    
    /**
     * 매장 상태 (OPEN, CLOSED, TEMPORARILY_CLOSED)
     */
    private String status;
}
