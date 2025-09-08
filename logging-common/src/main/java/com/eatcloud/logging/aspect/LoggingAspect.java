package com.eatcloud.logging.aspect;

import com.eatcloud.logging.annotation.Loggable;
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
import java.util.regex.Pattern;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    private final ObjectMapper objectMapper;
    
    // 생성자에 로그 추가
    public LoggingAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        log.info("🚀 LoggingAspect 빈이 생성되었습니다!");
    }
    
    // 민감한 정보 패턴들
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|pwd|passwd|pass)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)(email|mail)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?i)(phone|mobile|tel)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_PATTERN = Pattern.compile("(?i)(card|credit|debit)", Pattern.CASE_INSENSITIVE);

    // @Loggable 애노테이션이 있는 메서드 로깅
    @Around("@within(loggable) || @annotation(loggable)")
    public Object logLoggableMethod(ProceedingJoinPoint joinPoint, Loggable loggable) throws Throwable {
        return logMethodWithLoggableConfig(joinPoint, loggable, "ANNOTATED");
    }

    // Controller 메서드 로깅 (기본 설정)
    @Around("execution(* com.eatcloud.*.controller.*.*(..)) && !@within(com.eatcloud.logging.annotation.Loggable) && !@annotation(com.eatcloud.logging.annotation.Loggable)")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "CONTROLLER", getDefaultLoggableConfig());
    }

    // Service 메서드 로깅 (기본 설정)
    @Around("execution(* com.eatcloud.*.service.*.*(..)) && !@within(com.eatcloud.logging.annotation.Loggable) && !@annotation(com.eatcloud.logging.annotation.Loggable)")
    public Object logService(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "SERVICE", getDefaultLoggableConfig());
    }

    // Repository 메서드 로깅 (기본 설정) - JPA Repository 제외
    @Around("execution(* com.eatcloud.*.repository.*.*(..)) && !execution(* org.springframework.data.repository.Repository+.*(..)) && !@within(com.eatcloud.logging.annotation.Loggable) && !@annotation(com.eatcloud.logging.annotation.Loggable)")
    public Object logRepository(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "REPOSITORY", getDefaultLoggableConfig());
    }

    private Object logMethodWithLoggableConfig(ProceedingJoinPoint joinPoint, Loggable loggable, String layer) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 메서드 레벨 애노테이션이 없으면 클래스 레벨에서 찾기
        if (loggable == null) {
            loggable = joinPoint.getTarget().getClass().getAnnotation(Loggable.class);
        }
        
        return logMethodExecution(joinPoint, layer, loggable);
    }

    private Object logMethodExecution(ProceedingJoinPoint joinPoint, String layer, Loggable config) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();
        Object[] args = joinPoint.getArgs();
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 메서드 시작 로깅
            if (config.logParameters()) {
                logWithLevel(config.level(), "{} START - {}.{}() with args: {}", 
                        layer, className, methodName, formatArguments(args, config.maskSensitiveData()));
            } else {
                logWithLevel(config.level(), "{} START - {}.{}()", 
                        layer, className, methodName);
            }
            
            // 메서드 실행
            Object result = joinPoint.proceed();
            
            // 메서드 완료 로깅
            long duration = System.currentTimeMillis() - startTime;
            if (config.logExecutionTime() && config.logResult()) {
                logWithLevel(config.level(), "{} SUCCESS - {}.{}() completed in {}ms with result: {}", 
                        layer, className, methodName, duration, formatResult(result, config.maskSensitiveData()));
            } else if (config.logExecutionTime()) {
                logWithLevel(config.level(), "{} SUCCESS - {}.{}() completed in {}ms", 
                        layer, className, methodName, duration);
            } else if (config.logResult()) {
                logWithLevel(config.level(), "{} SUCCESS - {}.{}() with result: {}", 
                        layer, className, methodName, formatResult(result, config.maskSensitiveData()));
            } else {
                logWithLevel(config.level(), "{} SUCCESS - {}.{}()", 
                        layer, className, methodName);
            }
            
            return result;
            
        } catch (Exception e) {
            // 예외 발생 로깅
            long duration = System.currentTimeMillis() - startTime;
            log.error("{} ERROR - {}.{}() failed after {}ms with exception: {} - {}", 
                    layer, className, methodName, duration, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    private void logWithLevel(Loggable.LogLevel level, String message, Object... args) {
        switch (level) {
            case TRACE:
                log.trace(message, args);
                break;
            case DEBUG:
                log.debug(message, args);
                break;
            case INFO:
                log.info(message, args);
                break;
            case WARN:
                log.warn(message, args);
                break;
            case ERROR:
                log.error(message, args);
                break;
        }
    }

    private String formatArguments(Object[] args, boolean maskSensitive) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        
        try {
            return Arrays.stream(args)
                    .map(arg -> formatSingleArgument(arg, maskSensitive))
                    .reduce((a, b) -> a + ", " + b)
                    .map(s -> "[" + s + "]")
                    .orElse("[]");
        } catch (Exception e) {
            return "[Error formatting arguments: " + e.getMessage() + "]";
        }
    }

    private String formatSingleArgument(Object arg, boolean maskSensitive) {
        if (arg == null) {
            return "null";
        }
        
        if (isSimpleType(arg)) {
            String value = arg.toString();
            return maskSensitive ? maskSensitiveValue(value, arg.getClass().getSimpleName()) : value;
        }
        
        try {
            String json = objectMapper.writeValueAsString(arg);
            if (maskSensitive) {
                json = maskSensitiveData(json);
            }
            return json.length() > 200 ? json.substring(0, 200) + "..." : json;
        } catch (JsonProcessingException e) {
            return arg.getClass().getSimpleName() + "@" + Integer.toHexString(arg.hashCode());
        }
    }

    private String formatResult(Object result, boolean maskSensitive) {
        if (result == null) {
            return "null";
        }
        
        if (isSimpleType(result)) {
            String value = result.toString();
            return maskSensitive ? maskSensitiveValue(value, result.getClass().getSimpleName()) : value;
        }
        
        try {
            String json = objectMapper.writeValueAsString(result);
            if (maskSensitive) {
                json = maskSensitiveData(json);
            }
            return json.length() > 500 ? json.substring(0, 500) + "..." : json;
        } catch (JsonProcessingException e) {
            return result.getClass().getSimpleName() + "@" + Integer.toHexString(result.hashCode());
        }
    }

    private String maskSensitiveData(String json) {
        // JSON에서 민감한 필드들 마스킹
        json = json.replaceAll("(?i)\"(password|pwd|passwd|pass)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"***\"");
        json = json.replaceAll("(?i)\"(email|mail)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"***@***.***\"");
        json = json.replaceAll("(?i)\"(phone|mobile|tel)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"***-****-****\"");
        json = json.replaceAll("(?i)\"(card|credit|debit)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"****-****-****-****\"");
        json = json.replaceAll("(?i)\"(token|jwt|auth)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"***\"");
        return json;
    }

    private String maskSensitiveValue(String value, String fieldName) {
        if (PASSWORD_PATTERN.matcher(fieldName).find()) {
            return "***";
        }
        if (EMAIL_PATTERN.matcher(fieldName).find()) {
            return "***@***.***";
        }
        if (PHONE_PATTERN.matcher(fieldName).find()) {
            return "***-****-****";
        }
        if (CARD_PATTERN.matcher(fieldName).find()) {
            return "****-****-****-****";
        }
        return value;
    }

    private boolean isSimpleType(Object obj) {
        return obj instanceof String ||
               obj instanceof Number ||
               obj instanceof Boolean ||
               obj instanceof Character ||
               obj.getClass().isPrimitive();
    }

    private Loggable getDefaultLoggableConfig() {
        return new Loggable() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Loggable.class;
            }

            @Override
            public LogLevel level() {
                return LogLevel.INFO;
            }

            @Override
            public boolean logParameters() {
                return true;
            }

            @Override
            public boolean logResult() {
                return true;
            }

            @Override
            public boolean logExecutionTime() {
                return true;
            }

            @Override
            public boolean maskSensitiveData() {
                return false;
            }
        };
    }
}
