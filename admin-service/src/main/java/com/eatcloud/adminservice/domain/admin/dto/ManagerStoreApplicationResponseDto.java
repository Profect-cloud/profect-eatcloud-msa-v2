package com.eatcloud.adminservice.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "매니저·스토어 신청 응답 DTO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagerStoreApplicationResponseDto {

	@Schema(description = "신청 ID", example = "550e8400-e29b-41d4-a716-446655440000")
	private UUID applicationId;

	@Schema(description = "상태", example = "PENDING")
	private String status;

	@Schema(description = "신청 일시")
	private LocalDateTime createdAt;
}
