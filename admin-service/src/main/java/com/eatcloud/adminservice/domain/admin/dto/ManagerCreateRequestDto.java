package com.eatcloud.adminservice.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagerCreateRequestDto {

	@Schema(description = "로그인 이메일", example = "mgr@example.com")
	private String email;

	@Schema(description = "비밀번호", example = "P@ssw0rd!")
	private String password;

	@Schema(description = "매니저 이름", example = "홍길동")
	private String name;

	@Schema(description = "전화번호", example = "010-1234-5678")
	private String phoneNumber;

	@Schema(description = "할당할 가게 ID", example = "11111111-1111-1111-1111-111111111111")
	private UUID storeId;
}
