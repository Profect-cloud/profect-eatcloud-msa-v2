package com.eatcloud.autoresponse.core;

import org.springframework.http.HttpStatus;

public enum ApiResponseStatus {
	OK(HttpStatus.OK, "정상 처리되었습니다."),
	CREATED(HttpStatus.CREATED, "생성이 완료되었습니다."),
	BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "일치하는 값이 없습니다."),
	CONFLICT(HttpStatus.CONFLICT, "데이터 충돌이 발생했습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ""),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");

	private final HttpStatus httpStatus;
	private final String display;

	ApiResponseStatus(HttpStatus httpStatus, String display) {
		this.httpStatus = httpStatus;
		this.display = display;
	}
	public HttpStatus getHttpStatus() { return httpStatus; }
	public String getDisplay() { return display; }
}
