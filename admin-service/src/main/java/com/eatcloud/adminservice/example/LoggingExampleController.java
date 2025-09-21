package com.eatcloud.adminservice.example;

import com.eatcloud.logging.annotation.RecommendationLog;
import com.eatcloud.logging.annotation.StatefulLog;
import com.eatcloud.logging.annotation.StatelessLog;
import com.eatcloud.logging.util.TypedLogger;
import org.springframework.web.bind.annotation.*;

/**
 * 로깅 시스템 사용 예제 컨트롤러
 */
@RestController
@RequestMapping("/admin/logging-example")
public class LoggingExampleController {

    private static final TypedLogger logger = TypedLogger.getLogger(LoggingExampleController.class);

    /**
     * Stateful 로그 테스트 - 어노테이션 사용
     */
    @StatefulLog
    @PostMapping("/create-store")
    public String createStore(@RequestBody String storeData) {
        logger.info("Creating new store with data: {}", storeData);
        
        // 비즈니스 로직
        try {
            // 실제로는 데이터베이스에 저장
            Thread.sleep(100); // 시뮬레이션
            logger.info("Store created successfully");
            return "Store created successfully";
        } catch (Exception e) {
            logger.error("Failed to create store", e);
            throw new RuntimeException("Store creation failed", e);
        }
    }

    /**
     * Stateless 로그 테스트 - 어노테이션 사용
     */
    @StatelessLog
    @GetMapping("/stores")
    public String getStores(@RequestParam(defaultValue = "0") int page) {
        logger.info("Fetching stores list for page: {}", page);
        
        try {
            // 실제로는 데이터베이스에서 조회
            Thread.sleep(50); // 시뮬레이션
            logger.info("Successfully fetched {} stores", 10);
            return "Retrieved 10 stores";
        } catch (Exception e) {
            logger.error("Failed to fetch stores", e);
            throw new RuntimeException("Store fetch failed", e);
        }
    }

    /**
     * Recommendation 로그 테스트 - 어노테이션 사용
     */
    @RecommendationLog
    @PostMapping("/track-user-behavior")
    public String trackUserBehavior(@RequestBody String behaviorData) {
        logger.info("Tracking user behavior: {}", behaviorData);
        
        try {
            // 실제로는 추천 시스템에 데이터 전송
            Thread.sleep(30); // 시뮬레이션
            logger.info("User behavior tracked successfully");
            return "Behavior tracked";
        } catch (Exception e) {
            logger.error("Failed to track user behavior", e);
            throw new RuntimeException("Behavior tracking failed", e);
        }
    }

    /**
     * TypedLogger 직접 사용 예제
     */
    @PostMapping("/mixed-operations")
    public String mixedOperations(@RequestBody String data) {
        
        // Stateless 로그 - 데이터 조회
        logger.statelessInfo("Starting mixed operations - fetching user data");
        
        try {
            // 사용자 데이터 조회 (Stateless)
            Thread.sleep(50);
            logger.statelessInfo("User data fetched successfully");
            
            // 주문 생성 (Stateful)
            logger.statefulInfo("Creating new order with data: {}", data);
            Thread.sleep(100);
            logger.statefulInfo("Order created with ID: {}", "ORDER-12345");
            
            // 사용자 행동 추적 (Recommendation)
            logger.recommendationInfo("Tracking order creation behavior for user analytics");
            Thread.sleep(30);
            logger.recommendationInfo("Behavior data sent to recommendation engine");
            
            return "Mixed operations completed successfully";
            
        } catch (Exception e) {
            logger.error("Mixed operations failed", e);
            throw new RuntimeException("Operations failed", e);
        }
    }

    /**
     * 에러 로그 테스트
     */
    @GetMapping("/test-error")
    public String testError() {
        logger.statefulInfo("Testing error logging");
        
        try {
            // 의도적으로 에러 발생
            throw new IllegalArgumentException("This is a test error");
        } catch (Exception e) {
            logger.statefulError("Test error occurred", e);
            throw e;
        }
    }

    /**
     * 일반 로그 테스트 (기존 방식)
     */
    @GetMapping("/test-normal")
    public String testNormalLog() {
        logger.info("This is a normal log without specific type");
        logger.debug("Debug information");
        logger.warn("Warning message");
        
        return "Normal logging completed";
    }
}
