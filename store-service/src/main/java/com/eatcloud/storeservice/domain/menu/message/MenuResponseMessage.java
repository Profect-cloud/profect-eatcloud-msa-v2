package com.eatcloud.storeservice.domain.menu.message;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MenuResponseMessage {
    MENU_CREATE_SUCCESS("메뉴 등록 완료"),
    MENU_UPDATE_SUCCESS("메뉴 수정 완료"),
    MENU_DELETE_SUCCESS("메뉴 삭제 완료"),
    AI_DESCRIPTION_GENERATED("AI 메뉴 설명 생성 완료"),
    MENU_LIST_FETCH_SUCCESS("메뉴 목록 조회 성공"),
    MENU_DETAIL_FETCH_SUCCESS("메뉴 상세 조회 성공");

    private final String message;

    MenuResponseMessage(String message) {
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

