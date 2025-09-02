package com.eatcloud.storeservice.domain.store.dto;

import lombok.Getter;

import java.time.LocalTime;
import java.util.UUID;

@Getter
public class StoreCreateRequestDto {

    private UUID managerId;        // 사장님 ID (회원가입 후 매핑)

    private String storeName;      // 가게 이름
    private String storeAddress;   // 가게 주소
    private String phoneNumber;    // 전화번호

    private Integer minCost;       // 최소 주문 금액
    private String description;    // 가게 설명
    private Double storeLat;       // 위도
    private Double storeLon;       // 경도

    private Boolean openStatus;    // 영업 상태
    private LocalTime openTime;    // 오픈 시간
    private LocalTime closeTime;   // 마감 시간

    private Integer storeCategoryId; // 카테고리 ID
}
