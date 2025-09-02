package com.eatcloud.autoresponse.error;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiError {
	private final List<ApiFieldError> fieldErrors;
	private final String detail;

	public static ApiError of(List<ApiFieldError> fieldErrors, String detail) {
		return new ApiError(fieldErrors, detail);
	}
}
