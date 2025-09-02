package com.eatcloud.storeservice.domain.store.exception;

public class AiDescriptionException extends RuntimeException {
    private final AiErrorCode errorCode;

    public AiDescriptionException(AiErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public AiErrorCode getErrorCode() {
        return errorCode;
    }
}
