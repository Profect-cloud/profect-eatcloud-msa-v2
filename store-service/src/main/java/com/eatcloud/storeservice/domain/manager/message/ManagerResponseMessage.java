package com.eatcloud.storeservice.domain.manager.message;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ManagerResponseMessage {

    STORE_REGISTRATION_SUCCESS("가게 등록이 완료되었습니다."),
    STORE_CLOSURE_SUCCESS("가게 폐업 요청이 완료되었습니다."),
    STORE_UPDATE_SUCCESS("가게 정보 수정이 완료되었습니다."),
    MENU_CREATE_SUCCESS("메뉴 등록이 완료되었습니다."),
    MENU_UPDATE_SUCCESS("메뉴 수정이 완료되었습니다."),
    MENU_DELETE_SUCCESS("메뉴 삭제가 완료되었습니다."),
    AI_DESCRIPTION_SUCCESS("AI 메뉴 설명 생성이 완료되었습니다.");

    private final String message;

    ManagerResponseMessage(String message) {
        this.message = message;
    }

    @JsonValue
    public String getMessage() {
        return message;
    }

    public String format(Object... args) {
        return String.format(this.message, args);
    }
}

