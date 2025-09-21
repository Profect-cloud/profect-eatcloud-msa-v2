package com.eatcloud.logging.util;

import com.eatcloud.logging.enums.LogType;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 로그 타입을 MDC에 설정하고 관리하는 유틸리티 클래스
 */
public class LogTypeHelper {
    
    private static final String LOG_TYPE_KEY = "logType";
    
    /**
     * 로그 타입을 MDC에 설정
     */
    public static void setLogType(LogType logType) {
        if (logType != null) {
            MDC.put(LOG_TYPE_KEY, logType.getValue());
        } else {
            // null인 경우 기본값으로 stateless 설정
            MDC.put(LOG_TYPE_KEY, LogType.STATELESS.getValue());
        }
    }
    
    /**
     * 현재 설정된 로그 타입 반환
     * 설정되지 않은 경우 기본값으로 "stateless" 반환
     */
    public static String getCurrentLogType() {
        String logType = MDC.get(LOG_TYPE_KEY);
        return logType != null ? logType : LogType.STATELESS.getValue();
    }
    
    /**
     * 로그 타입 MDC 정리
     */
    public static void clearLogType() {
        MDC.remove(LOG_TYPE_KEY);
    }
    
    /**
     * 코드 블록 실행 시 로그 타입을 자동으로 설정하고 정리
     */
    public static void withLogType(LogType logType, Runnable action) {
        String previousLogType = getCurrentLogType();
        try {
            setLogType(logType);
            action.run();
        } finally {
            if (previousLogType != null) {
                MDC.put(LOG_TYPE_KEY, previousLogType);
            } else {
                clearLogType();
            }
        }
    }
    
    /**
     * Stateful 로그 설정 (상태 변경 작업)
     */
    public static void setStatefulLog() {
        setLogType(LogType.STATEFUL);
    }
    
    /**
     * Stateless 로그 설정 (조회 작업)
     */
    public static void setStatelessLog() {
        setLogType(LogType.STATELESS);
    }
    
    /**
     * 기본 로그 타입 설정 (기본값: stateless)
     */
    public static void setDefaultLogType() {
        setLogType(LogType.STATELESS);
    }
    
    /**
     * Recommendation 로그 설정 (추천 이벤트)
     */
    public static void setRecommendationLog() {
        setLogType(LogType.RECOMMENDATION);
    }
    
    /**
     * LogType별 전용 로거 사용
     */
    public static void logStateless(String message, Object... args) {
        LoggerFactory.getLogger("stateless").info(message, args);
    }
    
    public static void logStateful(String message, Object... args) {
        LoggerFactory.getLogger("stateful").info(message, args);
    }
    
    public static void logRecommendation(String message, Object... args) {
        LoggerFactory.getLogger("recommendation").info(message, args);
    }
}
