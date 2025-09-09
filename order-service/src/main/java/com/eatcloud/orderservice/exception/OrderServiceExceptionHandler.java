package com.eatcloud.orderservice.exception;

import com.eatcloud.orderservice.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
@Slf4j
public class OrderServiceExceptionHandler {

    @ExceptionHandler(CartException.class)
    public ResponseEntity<ApiResponse<Object>> handleCartException(CartException e) {
        log.error("Cart exception occurred: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getMessage()));
    }

    @ExceptionHandler(OrderException.class)
    public ResponseEntity<ApiResponse<Object>> handleOrderException(OrderException e) {
        log.error("Order exception occurred: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("Illegal argument exception: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiResponse<Object>> handleRestClientException(RestClientException e) {
        log.error("Rest client exception occurred: {}", e.getMessage(), e);
        
        String message = e.getMessage();
        if (message != null && message.contains("customer-service")) {
            return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("고객 서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해주세요."));
        } else if (message != null && message.contains("store-service")) {
            return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("매장 서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해주세요."));
        }
        
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("외부 서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해주세요."));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException e) {
        log.error("Runtime exception occurred: {}", e.getMessage(), e);
        
        String message = e.getMessage();

        if (message != null) {
            if (message.contains("포인트가 부족합니다") || message.contains("포인트는 주문 총액을 초과할 수 없습니다")) {
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(message));
            } else if (message.contains("포인트 검증에 실패했습니다")) {
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(message));
            } else if (message.contains("사용자 포인트를 조회할 수 없습니다")) {
                return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(message));
            } else if (message.contains("Customer service is temporarily unavailable")) {
                return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("고객 서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해주세요."));
            } else if (message.contains("Store service is temporarily unavailable")) {
                return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("매장 서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해주세요."));
            }
        }
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 오류가 발생했습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        log.error("Unexpected exception occurred: {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 오류가 발생했습니다."));
    }
}
