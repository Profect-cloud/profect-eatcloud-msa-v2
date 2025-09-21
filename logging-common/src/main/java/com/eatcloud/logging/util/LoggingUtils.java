package com.eatcloud.logging.util;

import com.eatcloud.logging.mdc.MDCUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

@Slf4j
public class LoggingUtils {
    
    /**
     * 비즈니스 로직 실행 시간을 측정하고 로깅
     */
    public static <T> T executeWithLogging(String operation, Supplier<T> supplier) {
        long startTime = System.currentTimeMillis();
        String requestId = MDCUtil.getRequestId();
        
        log.info("OPERATION START - {} [requestId: {}]", operation, requestId);
        
        try {
            T result = supplier.get();
            long duration = System.currentTimeMillis() - startTime;
            log.info("OPERATION SUCCESS - {} completed in {}ms [requestId: {}]", operation, duration, requestId);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("OPERATION ERROR - {} failed after {}ms [requestId: {}]: {}", operation, duration, requestId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 비즈니스 로직 실행 시간을 측정하고 로깅 (반환값 없음)
     */
    public static void executeWithLogging(String operation, Runnable runnable) {
        executeWithLogging(operation, () -> {
            runnable.run();
            return null;
        });
    }
    
    /**
     * 민감한 데이터 마스킹
     */
    public static String maskSensitiveData(String data) {
        if (data == null || data.length() <= 4) {
            return "****";
        }
        
        if (data.contains("@")) {
            // 이메일 마스킹
            String[] parts = data.split("@");
            if (parts.length == 2) {
                String localPart = parts[0];
                String domainPart = parts[1];
                
                if (localPart.length() <= 2) {
                    return "**@" + domainPart;
                } else {
                    return localPart.substring(0, 2) + "***@" + domainPart;
                }
            }
        }
        
        // 일반 문자열 마스킹
        if (data.length() <= 6) {
            return data.substring(0, 2) + "***";
        } else {
            return data.substring(0, 3) + "***" + data.substring(data.length() - 2);
        }
    }
    
    /**
     * 로그 메시지에 context 정보 추가
     */
    public static String enrichLogMessage(String message) {
        String requestId = MDCUtil.getRequestId();
        String userId = MDCUtil.getUserId();
        String serviceName = MDCUtil.getServiceName();
        
        StringBuilder enriched = new StringBuilder(message);
        
        if (requestId != null || userId != null || serviceName != null) {
            enriched.append(" [");
            if (serviceName != null) {
                enriched.append("service:").append(serviceName);
            }
            if (requestId != null) {
                if (serviceName != null) enriched.append(", ");
                enriched.append("requestId:").append(requestId);
            }
            if (userId != null) {
                if (serviceName != null || requestId != null) enriched.append(", ");
                enriched.append("userId:").append(userId);
            }
            enriched.append("]");
        }
        
        return enriched.toString();
    }
}
