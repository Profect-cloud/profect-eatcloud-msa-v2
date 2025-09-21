package com.eatcloud.logging.aspect;

import com.eatcloud.logging.mdc.MDCUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
public class PerformanceAspect {

    private static final long SLOW_QUERY_THRESHOLD = 1000; // 1초

    // 성능 모니터링이 필요한 메서드들
    @Around("execution(* com.eatcloud.*.service.*.*(..)) || " +
            "execution(* com.eatcloud.*.repository.*.*(..)) || " +
            "execution(* com.eatcloud.*.client.*.*(..))")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();
        
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            
            long duration = System.currentTimeMillis() - startTime;
            
            // 느린 쿼리 또는 메서드 실행 감지
            if (duration > SLOW_QUERY_THRESHOLD) {
                log.warn("SLOW EXECUTION DETECTED - {}.{}() took {}ms (threshold: {}ms)", 
                        className, methodName, duration, SLOW_QUERY_THRESHOLD);
            } else {
                log.debug("PERFORMANCE - {}.{}() executed in {}ms", 
                        className, methodName, duration);
            }
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("PERFORMANCE ERROR - {}.{}() failed after {}ms", 
                    className, methodName, duration);
            throw e;
        }
    }
}
