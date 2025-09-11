package com.eatcloud.logging.recommendation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 추천 시스템용 이벤트 데이터 DTO
 * 
 * 5가지 이벤트 타입:
 * - USER_SEARCH: 사용자 검색
 * - STORE_CLICK: 매장 클릭
 * - STORE_VIEW: 매장 상세 조회
 * - ADD_TO_CART: 장바구니 담기
 * - ORDER_COMPLETE: 주문 완료
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationEvent {
    
    /**
     * 추천 이벤트 타입
     */
    public enum EventType {
        USER_SEARCH,    // 사용자 검색
        STORE_CLICK,    // 매장 클릭
        STORE_VIEW,     // 매장 상세 조회
        ADD_TO_CART,    // 장바구니 담기
        ORDER_COMPLETE  // 주문 완료
    }
    
    /**
     * 이벤트 타입
     */
    private EventType eventType;
    
    /**
     * 사용자 ID
     */
    private String userId;
    
    /**
     * 세션 ID
     */
    private String sessionId;
    
    /**
     * 이벤트 발생 시간
     */
    private LocalDateTime timestamp;
    
    /**
     * 검색 키워드 (USER_SEARCH 타입에서 사용)
     */
    private String searchKeyword;
    
    /**
     * 매장 ID (STORE_CLICK, STORE_VIEW, ADD_TO_CART에서 사용)
     */
    private String storeId;
    
    /**
     * 매장 이름
     */
    private String storeName;
    
    /**
     * 카테고리
     */
    private String category;
    
    /**
     * 검색 결과 매장 ID 목록 (USER_SEARCH 타입에서 사용)
     */
    private List<String> resultStoreIds;
    
    /**
     * 주문 ID (ORDER_COMPLETE 타입에서 사용)
     */
    private String orderId;
    
    /**
     * 주문 금액 (ORDER_COMPLETE 타입에서 사용)
     */
    private Integer orderAmount;
    
    /**
     * 추가 메타데이터
     */
    private Map<String, Object> metadata;
    
    /**
     * 사용자 위치 정보
     */
    private LocationInfo location;
    
    /**
     * 디바이스 정보
     */
    private DeviceInfo device;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationInfo {
        private Double latitude;
        private Double longitude;
        private String address;
        private String district;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceInfo {
        private String deviceType; // WEB, MOBILE_APP, MOBILE_WEB
        private String userAgent;
        private String ip;
    }
    
    /**
     * 현재 시간으로 timestamp 설정
     */
    public void setCurrentTimestamp() {
        this.timestamp = LocalDateTime.now();
    }
    
    /**
     * 사용자 검색 이벤트 생성
     */
    public static RecommendationEvent createSearchEvent(String userId, String sessionId, 
                                                       String keyword, List<String> resultStoreIds) {
        return RecommendationEvent.builder()
                .eventType(EventType.USER_SEARCH)
                .userId(userId)
                .sessionId(sessionId)
                .searchKeyword(keyword)
                .resultStoreIds(resultStoreIds)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 매장 클릭 이벤트 생성
     */
    public static RecommendationEvent createStoreClickEvent(String userId, String sessionId, 
                                                           String storeId, String storeName, String category) {
        return RecommendationEvent.builder()
                .eventType(EventType.STORE_CLICK)
                .userId(userId)
                .sessionId(sessionId)
                .storeId(storeId)
                .storeName(storeName)
                .category(category)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 매장 상세 조회 이벤트 생성
     */
    public static RecommendationEvent createStoreViewEvent(String userId, String sessionId, 
                                                          String storeId, String storeName, String category) {
        return RecommendationEvent.builder()
                .eventType(EventType.STORE_VIEW)
                .userId(userId)
                .sessionId(sessionId)
                .storeId(storeId)
                .storeName(storeName)
                .category(category)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 장바구니 담기 이벤트 생성
     */
    public static RecommendationEvent createAddToCartEvent(String userId, String sessionId, 
                                                          String storeId, String storeName, String category) {
        return RecommendationEvent.builder()
                .eventType(EventType.ADD_TO_CART)
                .userId(userId)
                .sessionId(sessionId)
                .storeId(storeId)
                .storeName(storeName)
                .category(category)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 주문 완료 이벤트 생성
     */
    public static RecommendationEvent createOrderCompleteEvent(String userId, String sessionId, 
                                                              String orderId, String storeId, String storeName, 
                                                              String category, Integer orderAmount) {
        return RecommendationEvent.builder()
                .eventType(EventType.ORDER_COMPLETE)
                .userId(userId)
                .sessionId(sessionId)
                .orderId(orderId)
                .storeId(storeId)
                .storeName(storeName)
                .category(category)
                .orderAmount(orderAmount)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
