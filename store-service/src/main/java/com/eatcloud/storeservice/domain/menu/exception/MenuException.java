package com.eatcloud.storeservice.domain.menu.exception;

public class MenuException extends RuntimeException {
    private final MenuErrorCode errorCode;

    public MenuException(MenuErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public MenuErrorCode getErrorCode() {
        return errorCode;
    }
}
