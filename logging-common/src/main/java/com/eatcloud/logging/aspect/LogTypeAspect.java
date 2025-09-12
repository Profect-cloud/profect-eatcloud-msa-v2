package com.eatcloud.logging.aspect;

import com.eatcloud.logging.annotation.LogType;
import com.eatcloud.logging.annotation.RecommendationLog;
import com.eatcloud.logging.annotation.StatefulLog;
import com.eatcloud.logging.annotation.StatelessLog;
import com.eatcloud.logging.util.LogTypeHelper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 로그 타입 어노테이션을 처리하는 AOP Aspect
 */
@Aspect
@Component
public class LogTypeAspect {

    private static final Logger logger = LoggerFactory.getLogger(LogTypeAspect.class);

    @Around("@annotation(com.eatcloud.logging.annotation.LogType)")
    public Object handleLogType(ProceedingJoinPoint joinPoint) throws Throwable {
        LogType logTypeAnnotation = getMethodAnnotation(joinPoint, LogType.class);
        if (logTypeAnnotation != null) {
            return executeWithLogType(joinPoint, logTypeAnnotation.value());
        }
        return joinPoint.proceed();
    }

    @Around("@annotation(com.eatcloud.logging.annotation.StatefulLog)")
    public Object handleStatefulLog(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithLogType(joinPoint, com.eatcloud.logging.enums.LogType.STATEFUL);
    }

    @Around("@annotation(com.eatcloud.logging.annotation.StatelessLog)")
    public Object handleStatelessLog(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithLogType(joinPoint, com.eatcloud.logging.enums.LogType.STATELESS);
    }

    @Around("@annotation(com.eatcloud.logging.annotation.RecommendationLog)")
    public Object handleRecommendationLog(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithLogType(joinPoint, com.eatcloud.logging.enums.LogType.RECOMMENDATION);
    }

    private Object executeWithLogType(ProceedingJoinPoint joinPoint, com.eatcloud.logging.enums.LogType logType) throws Throwable {
        String previousLogType = LogTypeHelper.getCurrentLogType();
        try {
            LogTypeHelper.setLogType(logType);
            
            // 메서드 실행 전 로그
            logger.debug("Executing {} with logType: {}", 
                joinPoint.getSignature().toShortString(), logType.getValue());
            
            Object result = joinPoint.proceed();
            
            // 메서드 실행 후 로그
            logger.debug("Completed {} with logType: {}", 
                joinPoint.getSignature().toShortString(), logType.getValue());
                
            return result;
        } finally {
            // 이전 로그 타입 복원
            if (previousLogType != null) {
                LogTypeHelper.setLogType(getLogTypeByValue(previousLogType));
            } else {
                LogTypeHelper.clearLogType();
            }
        }
    }

    private <T extends java.lang.annotation.Annotation> T getMethodAnnotation(ProceedingJoinPoint joinPoint, Class<T> annotationClass) {
        try {
            Method method = joinPoint.getTarget().getClass()
                .getMethod(joinPoint.getSignature().getName(),
                    ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getParameterTypes());
            return method.getAnnotation(annotationClass);
        } catch (NoSuchMethodException e) {
            logger.debug("Could not find method annotation: {}", e.getMessage());
            return null;
        }
    }

    private com.eatcloud.logging.enums.LogType getLogTypeByValue(String value) {
        for (com.eatcloud.logging.enums.LogType logType : com.eatcloud.logging.enums.LogType.values()) {
            if (logType.getValue().equals(value)) {
                return logType;
            }
        }
        return null;
    }
}
