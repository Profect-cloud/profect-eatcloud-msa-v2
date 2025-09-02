package com.eatcloud.managerservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.eatcloud.managerservice.entity.Manager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ManagerDto {
	private UUID id;
	private String email;
	private String username;
	private String phoneNumber;

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public static ManagerDto from(Manager e) {
		return ManagerDto.builder()
			.id(e.getId())
			.email(e.getEmail())
			.username(e.getName())
			.phoneNumber(e.getPhoneNumber())
			.createdAt(e.getCreatedAt())
			.updatedAt(e.getUpdatedAt())
			.build();
	}
}
