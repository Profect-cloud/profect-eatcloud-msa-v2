package com.eatcloud.adminservice.domain.admin.exception;

import com.eatcloud.autoresponse.error.ErrorCode;

public enum AdminErrorCode implements ErrorCode {
	ADMIN_NOT_FOUND("ADMIN_001", "해당 관리자를 찾을 수 없습니다"),
	EMAIL_ALREADY_EXISTS("ADMIN_002", "이미 사용 중인 이메일입니다"),
	INVALID_INPUT("ADMIN_003", "잘못된 입력값입니다"),
	STORE_NOT_FOUND("ADMIN_004", "해당 매장이 존재하지 않습니다"),
	CATEGORY_NOT_FOUND("ADMIN_005", "해당 카테고리가 존재하지 않습니다"),
	CUSTOMER_NOT_FOUND("ADMIN_006", "해당 고객을 찾을 수 없습니다"),
	MANAGER_NOT_FOUND("ADMIN_007", "해당 매니저를 찾을 수 없습니다"),
	APPLICATION_NOT_FOUND("ADMIN_008", "해당 신청서를 찾을 수 없습니다"),
	APPLICATION_ALREADY_PROCESSED("ADMIN_009", "이미 처리된 신청서입니다"),
	APPLICATION_EMAIL_ALREADY_EXISTS("ADMIN_010", "이미 해당 이메일로 신청된 이력이 있습니다"),
	STORE_SERVICE_FAILED("ADMIN_011", "스토어 서비스 호출에 실패했습니다"),
	STORE_SERVICE_TIMEOUT("ADMIN_012", "스토어 서비스 응답 시간 초과"),
	STORE_SERVICE_BAD_REQUEST("ADMIN_013", "스토어 서비스에 잘못된 요청입니다"),
	IDEMPOTENCY_CONFLICT("ADMIN_014", "이미 처리된 요청입니다(멱등키 충돌)"),
	UNAUTHORIZED_INTERNAL_CALL("ADMIN_015", "내부 서비스 인증/권한 오류"),
	MANAGER_SERVICE_FAILED("ADMIN_016", "매니저 서비스 호출에 실패했습니다"),
	MANAGER_SERVICE_TIMEOUT("ADMIN_017", "매니저 서비스 응답 시간 초과"),
	MANAGER_SERVICE_BAD_REQUEST("ADMIN_018", "매니저 서비스에 잘못된 요청입니다"),
	CUSTOMER_SERVICE_FAILED("ADMIN_019", "고객 서비스 호출에 실패했습니다");


	private final String code;
	private final String message;

	AdminErrorCode(String code, String message) {
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

