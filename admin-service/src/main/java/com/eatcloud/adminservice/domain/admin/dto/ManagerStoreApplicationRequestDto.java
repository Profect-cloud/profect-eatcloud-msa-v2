package com.eatcloud.adminservice.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Schema(description = "매니저·스토어 신청 요청 DTO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagerStoreApplicationRequestDto {

	@NotBlank
	@Size(max = 20)
	@Schema(description = "신청자(매니저) 이름", example = "홍길동")
	private String managerName;

	@NotBlank
	@Email
	@Size(max = 255)
	@Schema(description = "신청자 이메일", example = "mgr@example.com")
	private String managerEmail;

	@NotBlank
	@Size(max = 255)
	@Schema(description = "신청자 비밀번호", example = "P@ssw0rd!")
	private String managerPassword;

	@Size(max = 18)
	@Schema(description = "신청자 연락처", example = "010-1234-5678")
	private String managerPhoneNumber;

	@NotBlank
	@Size(max = 200)
	@Schema(description = "스토어 이름", example = "우리동네피자")
	private String storeName;

	@Size(max = 300)
	@Schema(description = "스토어 주소", example = "서울시 강남구 ...")
	private String storeAddress;

	@Size(max = 18)
	@Schema(description = "스토어 연락처", example = "02-123-4567")
	private String storePhoneNumber;

	@Schema(description = "카테고리 ID", example = "1")
	private Integer categoryId;

	@Schema(description = "스토어 설명", example = "정통 화덕 피자 전문점")
	private String description;
}

