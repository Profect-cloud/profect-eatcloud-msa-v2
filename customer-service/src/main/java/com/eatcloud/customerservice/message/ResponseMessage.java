package com.eatcloud.customerservice.message;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ResponseMessage {
	PROFILE_UPDATE_SUCCESS("프로필 수정이 완료되었습니다"),
	PROFILE_INQUIRY_SUCCESS("프로필 조회가 완료되었습니다"),
	CUSTOMER_WITHDRAW_SUCCESS("고객 탈퇴가 완료되었습니다"),
	ADDRESS_SELECT_SUCCESS("기본 주소 설정이 완료되었습니다."),
	CART_ADD_SUCCESS("장바구니에 메뉴가 추가되었습니다"),
	CART_UPDATE_SUCCESS("장바구니 메뉴 수량이 변경되었습니다"),
	CART_ITEM_DELETE_SUCCESS("장바구니 메뉴가 삭제되었습니다"),
	CART_CLEAR_SUCCESS("장바구니가 전체 삭제되었습니다"),
	ADDRESS_DELETE_SUCCESS("주소 삭제가 완료 되었습니다.");

	private final String message;

	ResponseMessage(String message) {
		this.message = message;
	}

	@JsonValue
	public String getMessage() {
		return this.message;
	}

	public String format(Object... args) {
		return String.format(this.message, args);
	}
}