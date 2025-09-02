package com.eatcloud.adminservice.domain.admin.exception;

public class AdminException extends RuntimeException {
	private final AdminErrorCode errorCode;

	public AdminException(AdminErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public AdminException(AdminErrorCode errorCode, Throwable cause) {
		super(errorCode.getMessage(), cause);
		this.errorCode = errorCode;
	}

	public AdminErrorCode getErrorCode() {
		return errorCode;
	}
}