package com.eatcloud.logging.enums;

/**
 * 로그 타입 정의
 * - STATEFUL: 상태를 변경하는 작업 (주문, 결제, 재고 변경, 사용자 생성/수정 등)
 * - STATELESS: 상태를 변경하지 않는 작업 (조회, 검색, 캐시 읽기 등)  
 * - RECOMMENDATION: 추천 시스템 관련 이벤트 (사용자 행동 추적, 추천 결과 등)
 */
public enum LogType {
    STATEFUL("stateful", "상태 기반 로그"),
    STATELESS("stateless", "상태 비기반 로그"),
    RECOMMENDATION("recommendation", "추천 이벤트 로그");

    private final String value;
    private final String description;

    LogType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}
