package com.eatcloud.adminservice.domain.admin.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDto {
	private UUID id;
	private String name;
	private String email;
	private String password;
	private String phoneNumber;
	private String position;
	private String role;
}
