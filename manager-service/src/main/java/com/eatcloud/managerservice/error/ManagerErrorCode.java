package com.eatcloud.managerservice.error;

import org.springframework.http.HttpStatus;

import com.eatcloud.autoresponse.error.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ManagerErrorCode implements ErrorCode {
	MANAGER_NOT_FOUND("M001", "Manager를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	EMAIL_DUPLICATED("M002", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
	MANAGER_ALREADY_EXISTS("M003", "이미 존재하는 Manager입니다.", HttpStatus.CONFLICT);

	private final String code;
	private final String message;
	private final HttpStatus status;
}