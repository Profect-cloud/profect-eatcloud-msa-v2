package com.eatcloud.autoresponse.core;

import com.eatcloud.autoresponse.error.ApiError;
import com.eatcloud.autoresponse.error.ErrorCode;
import com.eatcloud.autoresponse.message.MessageResolvable;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

	private final int code;
	private final String message;
	private final T data;

	public static <T> ApiResponse<T> of(final ApiResponseStatus status, final T data) {
		return new ApiResponse<>(status.getHttpStatus().value(), status.getDisplay(), data);
	}

	public static <T> ApiResponse<T> with(final HttpStatus status, final String message, final T data) {
		return new ApiResponse<>(status.value(), message, data);
	}

	public static <T> ApiResponse<T> success(final T data) {
		return of(ApiResponseStatus.OK, data);
	}

	public static ApiResponse<Void> success() {
		return of(ApiResponseStatus.OK, null);
	}

	public static <T> ApiResponse<T> created(final T data) {
		return of(ApiResponseStatus.CREATED, data);
	}

	public static <T> ApiResponse<T> success(final MessageResolvable m, final T data) {
		return with(HttpStatus.OK, m.message(), data);
	}

	public static <T> ApiResponse<T> created(final MessageResolvable m, final T data) {
		return with(HttpStatus.CREATED, m.message(), data);
	}

	public static <T> ApiResponse<T> ok(final String message, final T data) {
		return with(HttpStatus.OK, message, data);
	}

	public static ApiResponse<ApiError> error(final HttpStatus status,
		final String message,
		final ApiError error) {
		return new ApiResponse<>(status.value(), message, error);
	}

	public static ApiResponse<ApiError> badRequest(final String message, final ApiError error) {
		return error(HttpStatus.BAD_REQUEST, message, error);
	}

	public static ApiResponse<ApiError> badRequest(final ApiError error) {
		return badRequest(ApiResponseStatus.BAD_REQUEST.getDisplay(), error);
	}

	public static ApiResponse<ApiError> notFound(final String message) {
		return error(HttpStatus.NOT_FOUND, message, null);
	}

	public static ApiResponse<ApiError> forbidden(final String message) {
		return error(HttpStatus.FORBIDDEN, message, null);
	}

	public static ApiResponse<ApiError> unauthorized(final String message) {
		return error(HttpStatus.UNAUTHORIZED, message, null);
	}

	public static ApiResponse<ApiError> conflict(final String message) {
		return error(HttpStatus.CONFLICT, message, null);
	}

	public static ApiResponse<ApiError> internalError(final String message) {
		return error(HttpStatus.INTERNAL_SERVER_ERROR, message, null);
	}

	public static ApiResponse<Void> from(final ErrorCode ec) {
		return with(ec.status(), ec.message(), null);
	}

	public static ApiResponse<ApiError> from(final ErrorCode ec, final ApiError error) {
		return error(ec.status(), ec.message(), error);
	}
}
