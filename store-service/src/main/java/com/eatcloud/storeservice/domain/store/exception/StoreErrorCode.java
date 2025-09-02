package com.eatcloud.storeservice.domain.store.exception;

import com.eatcloud.autoresponse.error.ErrorCode;

public enum StoreErrorCode implements ErrorCode {
    STORE_NOT_FOUND("STORE_001", "해당 매장을 찾을 수 없습니다."),
    STORE_ALREADY_REGISTERED("STORE_002", "이미 등록된 가게입니다."),
    STORE_APPLICATION_PENDING("STORE_003", "등록 요청이 이미 진행 중입니다."),
    STORE_ALREADY_CLOSED("STORE_004", "이미 폐업된 매장입니다."),
    NOT_AUTHORIZED("STORE_005", "해당 요청에 대한 권한이 없습니다."),
    CATEGORY_NOT_FOUND("STORE_006", "해당 카테고리를 찾을 수 없습니다.");

    private final String code;
    private final String message;

    StoreErrorCode(String code, String message) {
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
