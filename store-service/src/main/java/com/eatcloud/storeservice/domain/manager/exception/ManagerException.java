package com.eatcloud.storeservice.domain.manager.exception;

public class ManagerException extends RuntimeException {
    private final ManagerErrorCode errorCode;

    public ManagerException(ManagerErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ManagerErrorCode getErrorCode() {
        return errorCode;
    }
}

