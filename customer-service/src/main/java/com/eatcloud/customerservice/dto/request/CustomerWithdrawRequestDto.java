package com.eatcloud.customerservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CustomerWithdrawRequestDto(
	@NotBlank(message = "탈퇴 사유는 필수입니다")
	String reason
) {
	public CustomerWithdrawRequestDto {
		if (reason != null && reason.trim().isEmpty()) {
			throw new IllegalArgumentException("탈퇴 사유는 공백일 수 없습니다");
		}
	}
}