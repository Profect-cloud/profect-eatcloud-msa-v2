package com.eatcloud.autoresponse.error;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

	default String code(){
		return "";
	};

	default String message(){
		return "";
	}

	default HttpStatus status() {  // 상태를 안 넣어도 기본 BAD_REQUEST
		return HttpStatus.BAD_REQUEST;
	}
}
