package com.eatcloud.adminservice.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.UUID;

@Schema(description = "매니저 계정 정보 DTO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagerDto {

	@Schema(description = "매니저 고유 식별자 (18자리)", example = "A1B2C3D4E5F6G7H8I9")
	private UUID id;

	@Schema(description = "매니저 이름", example = "홍길동")
	private String name;

	@Schema(description = "이메일 주소", example = "owner@example.com")
	private String email;

	@Schema(description = "전화번호", example = "010-1234-5678")
	private String phoneNumber;

	@Schema(description = "할당된 가게 ID (18자리)", example = "Z9Y8X7W6V5U4T3S2R1")
	private UUID storeId;
}
