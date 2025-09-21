package com.eatcloud.logging.util;

import com.eatcloud.logging.enums.LogType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 구조화된 로깅을 위한 유틸리티 클래스
 */
public final class StructuredLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(StructuredLogger.class);
    
    private StructuredLogger() {
        // Utility class
    }
    
    /**
     * 상태 기반 로그 (Stateful) - 주문, 결제, 사용자 등록 등
     */
    public static void stateful(String message, Object... args) {
        LogTypeHelper.withLogType(LogType.STATEFUL, () -> logger.info(message, args));
    }
    
    /**
     * 상태 비기반 로그 (Stateless) - 조회, 검색, 캐시 등
     */
    public static void stateless(String message, Object... args) {
        LogTypeHelper.withLogType(LogType.STATELESS, () -> logger.info(message, args));
    }
    
    /**
     * 추천 이벤트 로그 (Recommendation) - 사용자 행동, 추천 결과 등
     */
    public static void recommendation(String message, Object... args) {
        LogTypeHelper.withLogType(LogType.RECOMMENDATION, () -> logger.info(message, args));
    }
    
    /**
     * 추천 이벤트 로그 with 추가 메타데이터
     */
    public static void recommendationEvent(String eventType, String itemId, Double score, String message, Object... args) {
        String previousLogType = LogTypeHelper.getCurrentLogType();
        try {
            LogTypeHelper.setLogType(LogType.RECOMMENDATION);
            MDC.put("eventType", eventType);
            MDC.put("itemId", itemId);
            if (score != null) {
                MDC.put("score", score.toString());
            }
            logger.info(message, args);
        } finally {
            MDC.remove("eventType");
            MDC.remove("itemId");
            MDC.remove("score");
            if (previousLogType != null) {
                MDC.put("logType", previousLogType);
            } else {
                LogTypeHelper.clearLogType();
            }
        }
    }
    
    /**
     * Stateful Error 로그
     */
    public static void statefulError(String message, Throwable throwable) {
        LogTypeHelper.withLogType(LogType.STATEFUL, () -> logger.error(message, throwable));
    }
    
    /**
     * Stateless Error 로그
     */
    public static void statelessError(String message, Throwable throwable) {
        LogTypeHelper.withLogType(LogType.STATELESS, () -> logger.error(message, throwable));
    }
    
    /**
     * Recommendation Error 로그
     */
    public static void recommendationError(String message, Throwable throwable) {
        LogTypeHelper.withLogType(LogType.RECOMMENDATION, () -> logger.error(message, throwable));
    }
    
    /**
     * Error 로그 (타입 지정)
     */
    public static void error(LogType logType, String message, Throwable throwable) {
        LogTypeHelper.withLogType(logType, () -> logger.error(message, throwable));
    }
    
    /**
     * Warn 로그 (타입 지정)
     */
    public static void warn(LogType logType, String message, Object... args) {
        LogTypeHelper.withLogType(logType, () -> logger.warn(message, args));
    }
    
    /**
     * Debug 로그 (타입 지정)
     */
    public static void debug(LogType logType, String message, Object... args) {
        LogTypeHelper.withLogType(logType, () -> logger.debug(message, args));
    }
}
