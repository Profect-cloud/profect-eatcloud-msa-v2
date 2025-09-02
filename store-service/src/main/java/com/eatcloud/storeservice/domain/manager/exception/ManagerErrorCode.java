package com.eatcloud.storeservice.domain.manager.exception;


public enum ManagerErrorCode {
    MANAGER_NOT_FOUND("MANAGER_001", "해당 매니저를 찾을 수 없습니다."),
    DUPLICATE_APPLICATION("MANAGER_002", "이미 등록 요청이 존재합니다."),
    NO_PERMISSION("MANAGER_003", "해당 요청에 대한 권한이 없습니다.");

    private final String code;
    private final String message;

    ManagerErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
