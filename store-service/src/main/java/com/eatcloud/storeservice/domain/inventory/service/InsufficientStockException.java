package com.eatcloud.storeservice.domain.inventory.service;

/**
 * 재고가 부족할 때 던지는 공용 예외.
 * Phase A/B/C 모든 경로(기본 RLock, Lua, Waitlist)에서 공통 사용 가능.
 */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException() {
        super("Insufficient stock available");
    }

    public InsufficientStockException(String message) {
        super(message);
    }

    public InsufficientStockException(String message, Throwable cause) {
        super(message, cause);
    }
}
