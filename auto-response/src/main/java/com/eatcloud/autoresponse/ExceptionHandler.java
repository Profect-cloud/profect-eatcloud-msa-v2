package com.eatcloud.autoresponse;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.eatcloud.autoresponse.core.ApiResponse;
import com.eatcloud.autoresponse.error.ApiError;
import com.eatcloud.autoresponse.error.ApiFieldError;
import com.eatcloud.autoresponse.error.BusinessException;
import com.eatcloud.autoresponse.error.ErrorCode;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ExceptionHandler {

	@org.springframework.web.bind.annotation.ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusiness(final BusinessException ex) {
		final ErrorCode ec = ex.getErrorCode();
		return ResponseEntity.status(ec.status()).body(ApiResponse.from(ec));
	}


	@org.springframework.web.bind.annotation.ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<ApiError>> handleInvalid(final MethodArgumentNotValidException ex) {
		final List<ApiFieldError> errors = ex.getBindingResult()
			.getFieldErrors().stream().map(this::toFieldError).collect(toList());
		return ResponseEntity.badRequest()
			.body(ApiResponse.badRequest(ApiError.of(errors, null)));
	}

	@org.springframework.web.bind.annotation.ExceptionHandler(BindException.class)
	public ResponseEntity<ApiResponse<ApiError>> handleBind(final BindException ex) {
		final List<ApiFieldError> errors = ex.getBindingResult()
			.getFieldErrors().stream().map(this::toFieldError).collect(toList());
		return ResponseEntity.badRequest()
			.body(ApiResponse.badRequest(ApiError.of(errors, null)));
	}

	@org.springframework.web.bind.annotation.ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiResponse<ApiError>> handleConstraint(final ConstraintViolationException ex) {
		final List<ApiFieldError> errors = ex.getConstraintViolations().stream()
			.map(v -> ApiFieldError.of(v.getPropertyPath().toString(), v.getInvalidValue(), v.getMessage()))
			.collect(toList());
		return ResponseEntity.badRequest()
			.body(ApiResponse.badRequest(ApiError.of(errors, null)));
	}

	@org.springframework.web.bind.annotation.ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<ApiError>> handleNotReadable(final HttpMessageNotReadableException ex) {
		return ResponseEntity.badRequest()
			.body(ApiResponse.badRequest("요청 본문을 읽을 수 없습니다.", null));
	}

	@org.springframework.web.bind.annotation.ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiResponse<ApiError>> handleAccessDenied(final AccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(ApiResponse.forbidden("권한이 없습니다."));
	}

	@org.springframework.web.bind.annotation.ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<ApiResponse<ApiError>> handleNoSuchElement(final NoSuchElementException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(ApiResponse.notFound("일치하는 값이 없습니다."));
	}

	@org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<ApiError>> handleAny(final Exception ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ApiResponse.internalError("서버 내부 오류가 발생했습니다."));
	}

	private ApiFieldError toFieldError(final FieldError fe) {
		return ApiFieldError.of(fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage());
	}
}
