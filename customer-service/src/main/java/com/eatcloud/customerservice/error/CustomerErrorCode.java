package com.eatcloud.customerservice.error;

import org.springframework.http.HttpStatus;

import com.eatcloud.autoresponse.error.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CustomerErrorCode implements ErrorCode {
	CUSTOMER_NOT_FOUND("C001", "해당 고객을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	INVALID_CUSTOMER_ID("C002", "유효하지 않은 고객 ID입니다.", HttpStatus.BAD_REQUEST),
	EMAIL_ALREADY_EXISTS("C003", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
	NICKNAME_ALREADY_EXISTS("C004", "이미 사용 중인 닉네임입니다.", HttpStatus.CONFLICT),
	WITHDRAWAL_REASON_REQUIRED("C005", "탈퇴 사유를 입력해주세요.", HttpStatus.BAD_REQUEST),
	INVALID_EMAIL_FORMAT("C006", "올바른 이메일 형식이 아닙니다.", HttpStatus.BAD_REQUEST),
	INVALID_PHONE_FORMAT("C007", "올바른 전화번호 형식이 아닙니다.", HttpStatus.BAD_REQUEST),
	INVALID_UPDATE_REQUEST("C008", "잘못된 수정 요청입니다.", HttpStatus.BAD_REQUEST),
	CUSTOMER_ALREADY_WITHDRAWN("C009", "이미 탈퇴한 고객입니다.", HttpStatus.BAD_REQUEST),
	ADDRESS_NOT_FOUND("C010", "해당 배송지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	CART_NOT_FOUND("C011", "장바구니를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	EMPTY_CART("C012", "장바구니가 비어 있습니다.", HttpStatus.BAD_REQUEST),
	INVALID_CART_ITEM_REQUEST("C013", "잘못된 장바구니 요청입니다.", HttpStatus.BAD_REQUEST),
	CART_ITEM_NOT_FOUND("C014", "해당 메뉴가 장바구니에 없습니다.", HttpStatus.NOT_FOUND),
	CART_STORE_MISMATCH("C015", "다른 가게의 메뉴는 장바구니에 추가할 수 없습니다. 기존 장바구니를 비운 후 다시 시도해주세요.", HttpStatus.BAD_REQUEST),
	INVALID_ORDER_TYPE("C016", "유효하지 않은 주문 타입 코드입니다.", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String message;
	private final HttpStatus status;
}