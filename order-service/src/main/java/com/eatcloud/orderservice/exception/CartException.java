package com.eatcloud.orderservice.exception;

public class CartException extends RuntimeException {
    private final ErrorCode errorCode;

    public CartException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
