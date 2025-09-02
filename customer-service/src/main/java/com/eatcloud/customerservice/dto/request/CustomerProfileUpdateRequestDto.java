package com.eatcloud.customerservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProfileUpdateRequestDto {

	@Size(min = 2, max = 20, message = "닉네임은 2-20자 사이여야 합니다")
	private String nickname;

	@Email(message = "올바른 이메일 형식이어야 합니다")
	private String email;

	@Pattern(regexp = "^01[0-9]-\\d{4}-\\d{4}$", message = "올바른 휴대폰 번호 형식이어야 합니다")
	private String phoneNumber;
}