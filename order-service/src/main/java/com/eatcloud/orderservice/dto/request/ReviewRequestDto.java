package com.eatcloud.orderservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ReviewRequestDto(
	@NotNull(message = "주문 ID는 필수입니다.")
	UUID orderId,

	@NotNull(message = "평점은 필수입니다.")
	@DecimalMin(value = "1.0", message = "평점은 1.0 이상이어야 합니다.")
	@DecimalMax(value = "5.0", message = "평점은 5.0 이하여야 합니다.")
	BigDecimal rating,

	@NotBlank(message = "리뷰 내용은 필수입니다.")
	String content
) {}
