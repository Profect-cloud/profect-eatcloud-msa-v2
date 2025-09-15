package com.eatcloud.storeservice.domain.inventory;

import com.eatcloud.storeservice.domain.inventory.service.InventoryServiceImpl;
import com.eatcloud.storeservice.support.lock.RedisLockExecutor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(InventoryServiceImpl.InsufficientStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT) // 409
    public Map<String,Object> insufficient(Exception e, HttpServletRequest req) {
        log.warn("[INV][409] {} {}", req.getRequestURI(), e.toString());
        return Map.of("code", 409, "error", "INSUFFICIENT_STOCK", "message", e.getMessage());
    }

    @ExceptionHandler(RedisLockExecutor.LockTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE) // 503
    public Map<String,Object> lockTimeout(Exception e, HttpServletRequest req) {
        log.warn("[INV][503] {} {}", req.getRequestURI(), e.toString());
        return Map.of("code", 503, "error", "LOCK_TIMEOUT", "message", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) // 필요시 404 등으로 분기
    public Map<String,Object> badRequest(IllegalArgumentException e, HttpServletRequest req) {
        log.warn("[INV][400] {} {}", req.getRequestURI(), e.toString());
        return Map.of("code", 400, "error", "BAD_REQUEST", "message", e.getMessage());
    }

    // 마지막 캐치: 알 수 없는 500은 스택트레이스 로그 남김
    @ExceptionHandler(Throwable.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String,Object> unhandled(Throwable t, HttpServletRequest req) {
        log.error("[INV][500] {} UNHANDLED", req.getRequestURI(), t); // ← 스택트레이스 전체 출력
        return Map.of("code", 500, "error", "UNHANDLED", "message", t.getMessage());
    }
}
