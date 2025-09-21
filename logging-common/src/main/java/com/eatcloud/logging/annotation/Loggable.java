package com.eatcloud.logging.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Loggable {
    
    /**
     * 로그 레벨 지정
     */
    LogLevel level() default LogLevel.INFO;
    
    /**
     * 파라미터 로깅 여부
     */
    boolean logParameters() default true;
    
    /**
     * 반환값 로깅 여부
     */
    boolean logResult() default true;
    
    /**
     * 실행 시간 로깅 여부
     */
    boolean logExecutionTime() default true;
    
    /**
     * 민감한 정보 마스킹 여부
     */
    boolean maskSensitiveData() default false;
    
    enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }
}
