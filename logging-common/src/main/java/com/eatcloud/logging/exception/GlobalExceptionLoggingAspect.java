package com.eatcloud.logging.exception;

import com.eatcloud.logging.mdc.MDCUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 공통 예외 로깅 처리
 * 응답 형태는 강제하지 않고, 로깅만 담당
 */
@Slf4j
@Aspect
@Component
@Order(1000) // 가장 낮은 우선순위로 설정
public class GlobalExceptionLoggingAspect {

    @AfterThrowing(pointcut = "execution(* com.eatcloud.*.controller.*.*(..)) || " +
                              "execution(* com.eatcloud.*.service.*.*(..)) || " +
                              "execution(* com.eatcloud.*.repository.*.*(..))", 
                   throwing = "ex")
    public void logException(JoinPoint joinPoint, Exception ex) {
        String requestId = MDCUtil.getRequestId();
        String userId = MDCUtil.getUserId();
        String serviceName = MDCUtil.getServiceName();
        
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        
        log.error("EXCEPTION OCCURRED - Service: {}, RequestId: {}, UserId: {}, Method: {}.{}(), " +
                 "Exception: {} - Message: {}", 
                 serviceName, requestId, userId, className, methodName, 
                 ex.getClass().getSimpleName(), ex.getMessage(), ex);
        
    }
}
