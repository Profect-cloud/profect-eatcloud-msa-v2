package com.eatcloud.customerservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequestDto(
	@NotBlank(message = "우편번호는 필수입니다")
	@Size(max = 10, message = "우편번호는 10자 이내여야 합니다")
	String zipcode,

	@NotBlank(message = "도로명 주소는 필수입니다")
	@Size(max = 500, message = "도로명 주소는 500자 이내여야 합니다")
	String roadAddr,

	@Size(max = 200, message = "상세 주소는 200자 이내여야 합니다")
	String detailAddr
) {}