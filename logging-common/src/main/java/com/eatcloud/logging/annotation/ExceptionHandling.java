package com.eatcloud.logging.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 예외 처리 로깅 설정 애노테이션
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExceptionHandling {
    
    /**
     * 자동 예외 로깅 여부
     */
    boolean autoLog() default true;
    
    /**
     * 스택 트레이스 포함 여부
     */
    boolean includeStackTrace() default true;
    
    /**
     * 로그 레벨
     */
    Loggable.LogLevel level() default Loggable.LogLevel.ERROR;
    
    /**
     * 민감한 데이터 마스킹 여부
     */
    boolean maskSensitiveData() default true;
}
