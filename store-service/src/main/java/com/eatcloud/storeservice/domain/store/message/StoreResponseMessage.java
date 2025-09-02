package com.eatcloud.storeservice.domain.store.message;

import com.fasterxml.jackson.annotation.JsonValue;

public enum StoreResponseMessage {
    STORE_UPDATE_SUCCESS("가게 정보 수정 완료"),
    STORE_REGISTRATION_REQUEST_SUCCESS("가게 등록 요청 완료"),
    STORE_CLOSURE_REQUEST_SUCCESS("가게 폐업 요청 완료"),
    STORE_CATEGORY_SEARCH_SUCCESS("매장 카테고리 기반 검색 성공"),
    STORE_MENU_CATEGORY_SEARCH_SUCCESS("메뉴 카테고리 기반 검색 성공");

    private final String message;

    StoreResponseMessage(String message) {
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
