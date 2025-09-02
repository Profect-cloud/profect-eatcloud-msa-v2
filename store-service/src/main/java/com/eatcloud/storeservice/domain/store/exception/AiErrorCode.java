package com.eatcloud.storeservice.domain.store.exception;

import com.eatcloud.autoresponse.error.ErrorCode;

public enum AiErrorCode implements ErrorCode {
    AI_RESPONSE_PARSING_FAILED("AI_001", "AI 설명 응답 파싱 실패");

    private final String code;
    private final String message;

    AiErrorCode(String code, String message) {
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
