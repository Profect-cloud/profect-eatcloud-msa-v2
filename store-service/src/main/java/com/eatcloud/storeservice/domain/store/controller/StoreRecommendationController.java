//package com.eatcloud.storeservice.domain.store.controller;
//
//import com.eatcloud.autoresponse.core.ApiResponse;
//import com.eatcloud.logging.annotation.Loggable;
//import com.eatcloud.logging.recommendation.RecommendationEventLogger;
//import com.eatcloud.storeservice.domain.store.dto.*;
//import com.eatcloud.storeservice.domain.store.service.StoreService;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.domain.Page;
//import org.springframework.security.core.annotation.AuthenticationPrincipal;
//import org.springframework.security.oauth2.jwt.Jwt;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
///**
// * 매장 검색 및 추천 이벤트 로깅을 담당하는 Controller
// *
// * 이 Controller는 다음 추천 이벤트를 로깅합니다:
// * - USER_SEARCH: 사용자 검색
// * - STORE_CLICK: 매장 클릭
// * - STORE_VIEW: 매장 상세 조회
// */
//@Slf4j
//@RestController
//@RequestMapping("/api/v1/stores")
//@RequiredArgsConstructor
//@Tag(name = "5-2. StoreRecommendationController", description = "매장 검색 및 추천 API")
//@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true)
//public class StoreRecommendationController {
//
//    private final StoreService storeService;
//    private final RecommendationEventLogger recommendationEventLogger;
//
//    /**
//     * 키워드 기반 매장 검색 (추천 이벤트 로깅 포함)
//     */
//    @Operation(summary = "키워드 매장 검색 (추천 이벤트 로깅)",
//               description = "키워드로 매장을 검색하고 추천 시스템용 검색 이벤트를 로깅합니다.")
//    @GetMapping("/search/recommendation")
//    public ApiResponse<Page<StoreSearchResponseDto>> searchStoresWithRecommendation(
//            @ModelAttribute StoreKeywordSearchRequestDto request,
//            @AuthenticationPrincipal Jwt jwt,
//            @RequestHeader(value = "X-Session-ID", required = false) String sessionId) {
//
//        // 사용자 정보 추출
//        String userId = getUserId(jwt);
//        String userSessionId = sessionId != null ? sessionId : "anonymous";
//
//        log.info("Store search with recommendation logging - userId: {}, keyword: {}", userId, request.getKeyword());
//
//        // 매장 검색 실행
//        Page<StoreSearchResponseDto> searchResults = storeService.searchStoresByKeyword(request);
//
//        // 추천 이벤트 로깅 (검색 이벤트)
//        if (recommendationEventLogger != null && request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
//            List<String> storeIds = searchResults.getContent().stream()
//                    .map(store -> String.valueOf(store.getStoreId()))
//                    .collect(Collectors.toList());
//
//            try {
//                recommendationEventLogger.logSearchEvent(
//                        userId,
//                        request.getKeyword().trim(),
//                        storeIds,
//                        userSessionId
//                );
//                log.debug("Search event logged - userId: {}, keyword: {}, resultCount: {}",
//                         userId, request.getKeyword(), storeIds.size());
//            } catch (Exception e) {
//                log.warn("Failed to log search event", e);
//            }
//        }
//
//        return ApiResponse.success(searchResults);
//    }
//
//    /**
//     * 매장 클릭 이벤트 로깅
//     */
//    @Operation(summary = "매장 클릭 이벤트 로깅",
//               description = "사용자가 매장을 클릭했을 때 추천 시스템용 클릭 이벤트를 로깅합니다.")
//    @PostMapping("/{storeId}/click")
//    public ApiResponse<Void> logStoreClick(
//            @PathVariable Long storeId,
//            @RequestParam(required = false) String storeName,
//            @RequestParam(required = false) String category,
//            @AuthenticationPrincipal Jwt jwt,
//            @RequestHeader(value = "X-Session-ID", required = false) String sessionId) {
//
//        String userId = getUserId(jwt);
//        String userSessionId = sessionId != null ? sessionId : "anonymous";
//
//        log.info("Store click event - userId: {}, storeId: {}", userId, storeId);
//
//        // 추천 이벤트 로깅 (클릭 이벤트)
//        if (recommendationEventLogger != null) {
//            try {
//                recommendationEventLogger.logStoreClickEvent(
//                        userId,
//                        userSessionId,
//                        String.valueOf(storeId),
//                        storeName != null ? storeName : "Unknown Store",
//                        category != null ? category : "Unknown Category"
//                );
//                log.debug("Store click event logged - userId: {}, storeId: {}", userId, storeId);
//            } catch (Exception e) {
//                log.warn("Failed to log store click event", e);
//            }
//        }
//
//        return ApiResponse.success();
//    }
//
//    /**
//     * 매장 상세 조회 (추천 이벤트 로깅 포함)
//     */
//    @Operation(summary = "매장 상세 조회 (추천 이벤트 로깅)",
//               description = "매장 상세 정보를 조회하고 추천 시스템용 조회 이벤트를 로깅합니다.")
//    @GetMapping("/{storeId}/detail")
//    public ApiResponse<StoreDetailResponseDto> getStoreDetailWithRecommendation(
//            @PathVariable Long storeId,
//            @AuthenticationPrincipal Jwt jwt,
//            @RequestHeader(value = "X-Session-ID", required = false) String sessionId) {
//
//        String userId = getUserId(jwt);
//        String userSessionId = sessionId != null ? sessionId : "anonymous";
//
//        log.info("Store detail view with recommendation logging - userId: {}, storeId: {}", userId, storeId);
//
//        // 매장 상세 정보 조회 (실제 서비스 메서드는 구현 필요)
//        StoreDetailResponseDto storeDetail = getStoreDetail(storeId);
//
//        // 추천 이벤트 로깅 (매장 상세 조회 이벤트)
//        if (recommendationEventLogger != null && storeDetail != null) {
//            try {
//                recommendationEventLogger.logStoreViewEvent(
//                        userId,
//                        userSessionId,
//                        String.valueOf(storeId),
//                        storeDetail.getStoreName(),
//                        storeDetail.getCategory()
//                );
//                log.debug("Store view event logged - userId: {}, storeId: {}", userId, storeId);
//            } catch (Exception e) {
//                log.warn("Failed to log store view event", e);
//            }
//        }
//
//        return ApiResponse.success(storeDetail);
//    }
//
//    /**
//     * 카테고리별 매장 검색 (추천 이벤트 로깅 포함)
//     */
//    @Operation(summary = "카테고리별 매장 검색 (추천 이벤트 로깅)",
//               description = "카테고리별로 매장을 검색하고 추천 시스템용 검색 이벤트를 로깅합니다.")
//    @GetMapping("/search/category/recommendation")
//    public ApiResponse<List<StoreSearchResponseDto>> searchStoresByCategoryWithRecommendation(
//            @ModelAttribute StoreSearchRequestDto condition,
//            @AuthenticationPrincipal Jwt jwt,
//            @RequestHeader(value = "X-Session-ID", required = false) String sessionId) {
//
//        String userId = getUserId(jwt);
//        String userSessionId = sessionId != null ? sessionId : "anonymous";
//
//        log.info("Category search with recommendation logging - userId: {}, category: {}",
//                userId, condition.getStoreCategoryId());
//
//        // 매장 검색 실행
//        List<StoreSearchResponseDto> searchResults = storeService.searchStoresByCategoryAndDistance(condition);
//
//        // 추천 이벤트 로깅 (카테고리 검색 이벤트)
//        if (recommendationEventLogger != null && condition.getStoreCategoryId() != null) {
//            List<String> storeIds = searchResults.stream()
//                    .map(store -> String.valueOf(store.getStoreId()))
//                    .collect(Collectors.toList());
//
//            try {
//                // 카테고리 ID를 키워드로 사용 (실제로는 카테고리명을 사용하는 것이 좋음)
//                String categoryKeyword = "category_" + condition.getStoreCategoryId();
//                recommendationEventLogger.logSearchEvent(
//                        userId,
//                        categoryKeyword,
//                        storeIds,
//                        userSessionId
//                );
//                log.debug("Category search event logged - userId: {}, category: {}, resultCount: {}",
//                         userId, condition.getStoreCategoryId(), storeIds.size());
//            } catch (Exception e) {
//                log.warn("Failed to log category search event", e);
//            }
//        }
//
//        return ApiResponse.success(searchResults);
//    }
//
//    /**
//     * 추천 매장 목록 조회 (향후 추천 시스템 연동용)
//     */
//    @Operation(summary = "추천 매장 목록 조회",
//               description = "사용자 맞춤 추천 매장 목록을 조회합니다. (향후 추천 시스템 연동)")
//    @GetMapping("/recommendations")
//    public ApiResponse<List<StoreSearchResponseDto>> getRecommendedStores(
//            @AuthenticationPrincipal Jwt jwt,
//            @RequestParam(defaultValue = "10") int limit) {
//
//        String userId = getUserId(jwt);
//
//        log.info("Recommended stores request - userId: {}, limit: {}", userId, limit);
//
//        // TODO: 실제 추천 시스템과 연동
//        // 현재는 더미 데이터 또는 기본 매장 목록 반환
//        List<StoreSearchResponseDto> recommendedStores = getDefaultRecommendedStores(limit);
//
//        return ApiResponse.success(recommendedStores);
//    }
//
//    // ========== Private Helper Methods ==========
//
//    /**
//     * JWT에서 사용자 ID 추출
//     */
//    private String getUserId(Jwt jwt) {
//        if (jwt != null && jwt.getSubject() != null) {
//            return jwt.getSubject();
//        }
//        return "anonymous";
//    }
//
//    /**
//     * 매장 상세 정보 조회 (실제 구현 필요)
//     */
//    private StoreDetailResponseDto getStoreDetail(Long storeId) {
//        // TODO: 실제 StoreService에서 매장 상세 정보 조회 메서드 구현
//        return StoreDetailResponseDto.builder()
//                .storeId(storeId)
//                .storeName("Sample Store")
//                .category("한식")
//                .description("맛있는 한식 전문점")
//                .build();
//    }
//
//    /**
//     * 기본 추천 매장 목록 (실제 추천 시스템 연동 전 임시)
//     */
//    private List<StoreSearchResponseDto> getDefaultRecommendedStores(int limit) {
//        // TODO: 실제 추천 알고리즘 또는 인기 매장 조회
//        return List.of(); // 임시로 빈 리스트 반환
//    }
//}
