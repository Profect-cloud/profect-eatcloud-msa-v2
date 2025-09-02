package com.eatcloud.autoresponse.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiFieldError {
	private final String field;
	private final String rejectedValue;
	private final String reason;

	public static ApiFieldError of(String field, Object rejectedValue, String reason) {
		return new ApiFieldError(field,
			rejectedValue == null ? null : String.valueOf(rejectedValue),
			reason);
	}
}
