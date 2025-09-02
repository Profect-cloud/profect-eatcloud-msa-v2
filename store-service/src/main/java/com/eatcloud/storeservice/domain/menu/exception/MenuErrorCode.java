package com.eatcloud.storeservice.domain.menu.exception;

public enum MenuErrorCode {
    MENU_NOT_FOUND("MENU_001", "해당 메뉴를 찾을 수 없습니다."),
    MENU_STORE_MISMATCH("MENU_002", "해당 메뉴는 지정된 매장에 속하지 않습니다."),
    INVALID_MENU_REQUEST("MENU_003", "유효하지 않은 메뉴 요청입니다."),
    MENU_NAME_REQUIRED("MENU_004", "메뉴 이름은 필수입니다."),
    INVALID_MENU_PRICE("MENU_005", "가격은 0 이상이어야 합니다."),
    DUPLICATE_MENU_NUM("MENU_006", "해당 메뉴 번호는 이미 존재합니다.");

    private final String code;
    private final String message;

    MenuErrorCode(String code, String message) {
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

