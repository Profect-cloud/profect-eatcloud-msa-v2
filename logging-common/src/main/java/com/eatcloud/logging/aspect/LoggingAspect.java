package com.eatcloud.logging.aspect;

import com.eatcloud.logging.mdc.MDCUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LoggingAspect {

    private final ObjectMapper objectMapper;

    // Controller 메서드 로깅
    @Around("execution(* com.eatcloud.*.controller.*.*(..))")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "CONTROLLER");
    }

    // Service 메서드 로깅
    @Around("execution(* com.eatcloud.*.service.*.*(..))")
    public Object logService(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "SERVICE");
    }

    // Repository 메서드 로깅 (JPA Repository 제외)
    @Around("execution(* com.eatcloud.*.repository.*.*(..)) && !execution(* org.springframework.data.repository.Repository+.*(..))")
    public Object logRepository(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "REPOSITORY");
    }

    private Object logMethodExecution(ProceedingJoinPoint joinPoint, String layer) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();
        Object[] args = joinPoint.getArgs();
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 메서드 시작 로깅
            log.info("{} START - {}.{}() with args: {}", 
                    layer, className, methodName, formatArguments(args));
            
            // 메서드 실행
            Object result = joinPoint.proceed();
            
            // 메서드 완료 로깅
            long duration = System.currentTimeMillis() - startTime;
            log.info("{} SUCCESS - {}.{}() completed in {}ms with result: {}", 
                    layer, className, methodName, duration, formatResult(result));
            
            return result;
            
        } catch (Exception e) {
            // 예외 발생 로깅
            long duration = System.currentTimeMillis() - startTime;
            log.error("{} ERROR - {}.{}() failed after {}ms with exception: {} - {}", 
                    layer, className, methodName, duration, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    private String formatArguments(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        
        try {
            return Arrays.stream(args)
                    .map(this::formatSingleArgument)
                    .reduce((a, b) -> a + ", " + b)
                    .map(s -> "[" + s + "]")
                    .orElse("[]");
        } catch (Exception e) {
            return "[Error formatting arguments: " + e.getMessage() + "]";
        }
    }

    private String formatSingleArgument(Object arg) {
        if (arg == null) {
            return "null";
        }
        
        if (isSimpleType(arg)) {
            return arg.toString();
        }
        
        try {
            String json = objectMapper.writeValueAsString(arg);
            return json.length() > 200 ? json.substring(0, 200) + "..." : json;
        } catch (JsonProcessingException e) {
            return arg.getClass().getSimpleName() + "@" + Integer.toHexString(arg.hashCode());
        }
    }

    private String formatResult(Object result) {
        if (result == null) {
            return "null";
        }
        
        if (isSimpleType(result)) {
            return result.toString();
        }
        
        try {
            String json = objectMapper.writeValueAsString(result);
            return json.length() > 500 ? json.substring(0, 500) + "..." : json;
        } catch (JsonProcessingException e) {
            return result.getClass().getSimpleName() + "@" + Integer.toHexString(result.hashCode());
        }
    }

    private boolean isSimpleType(Object obj) {
        return obj instanceof String ||
               obj instanceof Number ||
               obj instanceof Boolean ||
               obj instanceof Character ||
               obj.getClass().isPrimitive();
    }
}
