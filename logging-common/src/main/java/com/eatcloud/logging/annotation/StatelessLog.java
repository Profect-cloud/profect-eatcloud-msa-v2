package com.eatcloud.logging.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 상태 비기반 로그 (Stateless) 어노테이션
 * 데이터를 조회만 하는 작업에 사용
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface StatelessLog {
}
