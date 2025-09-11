package com.eatcloud.logging.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 추천 이벤트 로거 (조건부 활성화)
 * 
 * application.properties에 logging.recommendation.enabled=true 설정 시 활성화
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "logging.recommendation.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RecommendationEventLogger {
    
    private static final Logger RECOMMENDATION_LOGGER = LoggerFactory.getLogger("RECOMMENDATION_EVENT");
    private final ObjectMapper objectMapper;
    
    /**
     * 추천 이벤트 로깅
     */
    public void logEvent(RecommendationEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            RECOMMENDATION_LOGGER.info("RECOMMENDATION_EVENT={}", eventJson);
            log.debug("Recommendation event logged: {}", event.getEventType());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize recommendation event: {}", event, e);
        }
    }
    
    /**
     * 사용자 검색 이벤트 로깅
     */
    public void logSearchEvent(String userId, String keyword, List<String> storeIds, String sessionId) {
        RecommendationEvent event = RecommendationEvent.createSearchEvent(userId, sessionId, keyword, storeIds);
        logEvent(event);
    }
    
    /**
     * 매장 클릭 이벤트 로깅
     */
    public void logStoreClickEvent(String userId, String sessionId, String storeId, String storeName, String category) {
        RecommendationEvent event = RecommendationEvent.createStoreClickEvent(userId, sessionId, storeId, storeName, category);
        logEvent(event);
    }
    
    /**
     * 매장 상세 조회 이벤트 로깅
     */
    public void logStoreViewEvent(String userId, String sessionId, String storeId, String storeName, String category) {
        RecommendationEvent event = RecommendationEvent.createStoreViewEvent(userId, sessionId, storeId, storeName, category);
        logEvent(event);
    }
    
    /**
     * 장바구니 담기 이벤트 로깅
     */
    public void logAddToCartEvent(String userId, String sessionId, String storeId, String storeName, String category) {
        RecommendationEvent event = RecommendationEvent.createAddToCartEvent(userId, sessionId, storeId, storeName, category);
        logEvent(event);
    }
    
    /**
     * 주문 완료 이벤트 로깅
     */
    public void logOrderCompleteEvent(String userId, String sessionId, String orderId, 
                                     String storeId, String storeName, String category, Integer orderAmount) {
        RecommendationEvent event = RecommendationEvent.createOrderCompleteEvent(
                userId, sessionId, orderId, storeId, storeName, category, orderAmount);
        logEvent(event);
    }
    
    /**
     * 위치 정보가 포함된 이벤트 로깅
     */
    public void logEventWithLocation(RecommendationEvent event, Double latitude, Double longitude, String address) {
        RecommendationEvent.LocationInfo location = RecommendationEvent.LocationInfo.builder()
                .latitude(latitude)
                .longitude(longitude)
                .address(address)
                .build();
        event.setLocation(location);
        logEvent(event);
    }
    
    /**
     * 디바이스 정보가 포함된 이벤트 로깅
     */
    public void logEventWithDevice(RecommendationEvent event, String deviceType, String userAgent, String ip) {
        RecommendationEvent.DeviceInfo device = RecommendationEvent.DeviceInfo.builder()
                .deviceType(deviceType)
                .userAgent(userAgent)
                .ip(ip)
                .build();
        event.setDevice(device);
        logEvent(event);
    }
}
