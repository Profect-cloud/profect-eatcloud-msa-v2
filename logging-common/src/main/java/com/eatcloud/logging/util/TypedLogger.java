package com.eatcloud.logging.util;

import com.eatcloud.logging.enums.LogType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 로그 타입별 전용 로거
 */
public class TypedLogger {

    private final Logger logger;

    private TypedLogger(Logger logger) {
        this.logger = logger;
    }

    public static TypedLogger getLogger(Class<?> clazz) {
        return new TypedLogger(LoggerFactory.getLogger(clazz));
    }

    public static TypedLogger getLogger(String name) {
        return new TypedLogger(LoggerFactory.getLogger(name));
    }

    // Stateful 로그 메서드들
    public void statefulInfo(String message) {
        LogTypeHelper.withLogType(LogType.STATEFUL, () -> logger.info(message));
    }

    public void statefulInfo(String message, Object... args) {
        LogTypeHelper.withLogType(LogType.STATEFUL, () -> logger.info(message, args));
    }

    public void statefulWarn(String message) {
        LogTypeHelper.withLogType(LogType.STATEFUL, () -> logger.warn(message));
    }

    public void statefulWarn(String message, Object... args) {
        LogTypeHelper.withLogType(LogType.STATEFUL, () -> logger.warn(message, args));
    }

    public void statefulError(String message) {
        LogTypeHelper.withLogType(LogType.STATEFUL, () -> logger.error(message));
    }

    public void statefulError(String message, Throwable throwable) {
        LogTypeHelper.withLogType(LogType.STATEFUL, () -> logger.error(message, throwable));
    }

    public void statefulError(String message, Object... args) {
        LogTypeHelper.withLogType(LogType.STATEFUL, () -> logger.error(message, args));
    }

    public void statefulDebug(String message) {
        LogTypeHelper.withLogType(LogType.STATEFUL, () -> logger.debug(message));
    }

    public void statefulDebug(String message, Object... args) {
        LogTypeHelper.withLogType(LogType.STATEFUL, () -> logger.debug(message, args));
    }

    // Stateless 로그 메서드들
    public void statelessInfo(String message) {
        LogTypeHelper.withLogType(LogType.STATELESS, () -> logger.info(message));
    }

    public void statelessInfo(String message, Object... args) {
        LogTypeHelper.withLogType(LogType.STATELESS, () -> logger.info(message, args));
    }

    public void statelessWarn(String message) {
        LogTypeHelper.withLogType(LogType.STATELESS, () -> logger.warn(message));
    }

    public void statelessWarn(String message, Object... args) {
        LogTypeHelper.withLogType(LogType.STATELESS, () -> logger.warn(message, args));
    }

    public void statelessError(String message) {
        LogTypeHelper.withLogType(LogType.STATELESS, () -> logger.error(message));
    }

    public void statelessError(String message, Throwable throwable) {
        LogTypeHelper.withLogType(LogType.STATELESS, () -> logger.error(message, throwable));
    }

    public void statelessError(String message, Object... args) {
        LogTypeHelper.withLogType(LogType.STATELESS, () -> logger.error(message, args));
    }

    public void statelessDebug(String message) {
        LogTypeHelper.withLogType(LogType.STATELESS, () -> logger.debug(message));
    }

    public void statelessDebug(String message, Object... args) {
        LogTypeHelper.withLogType(LogType.STATELESS, () -> logger.debug(message, args));
    }

    // Recommendation 로그 메서드들
    public void recommendationInfo(String message) {
        LogTypeHelper.withLogType(LogType.RECOMMENDATION, () -> logger.info(message));
    }

    public void recommendationInfo(String message, Object... args) {
        LogTypeHelper.withLogType(LogType.RECOMMENDATION, () -> logger.info(message, args));
    }

    public void recommendationWarn(String message) {
        LogTypeHelper.withLogType(LogType.RECOMMENDATION, () -> logger.warn(message));
    }

    public void recommendationWarn(String message, Object... args) {
        LogTypeHelper.withLogType(LogType.RECOMMENDATION, () -> logger.warn(message, args));
    }

    public void recommendationError(String message) {
        LogTypeHelper.withLogType(LogType.RECOMMENDATION, () -> logger.error(message));
    }

    public void recommendationError(String message, Throwable throwable) {
        LogTypeHelper.withLogType(LogType.RECOMMENDATION, () -> logger.error(message, throwable));
    }

    public void recommendationError(String message, Object... args) {
        LogTypeHelper.withLogType(LogType.RECOMMENDATION, () -> logger.error(message, args));
    }

    public void recommendationDebug(String message) {
        LogTypeHelper.withLogType(LogType.RECOMMENDATION, () -> logger.debug(message));
    }

    public void recommendationDebug(String message, Object... args) {
        LogTypeHelper.withLogType(LogType.RECOMMENDATION, () -> logger.debug(message, args));
    }

    // 기본 로그 메서드들 (기존 방식과 호환성 유지)
    public void info(String message) {
        logger.info(message);
    }

    public void info(String message, Object... args) {
        logger.info(message, args);
    }

    public void warn(String message) {
        logger.warn(message);
    }

    public void warn(String message, Object... args) {
        logger.warn(message, args);
    }

    public void error(String message) {
        logger.error(message);
    }

    public void error(String message, Throwable throwable) {
        logger.error(message, throwable);
    }

    public void error(String message, Object... args) {
        logger.error(message, args);
    }

    public void debug(String message) {
        logger.debug(message);
    }

    public void debug(String message, Object... args) {
        logger.debug(message, args);
    }

    // 로거 레벨 체크 메서드들
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }
}
