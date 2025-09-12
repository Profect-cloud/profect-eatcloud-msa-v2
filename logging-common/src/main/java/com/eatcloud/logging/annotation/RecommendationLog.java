package com.eatcloud.logging.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 추천 이벤트 로그 어노테이션
 * 사용자 행동 추적, 추천 결과 등에 사용
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RecommendationLog {
}
