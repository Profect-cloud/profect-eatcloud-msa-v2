package com.eatcloud.adminservice.domain.admin.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLoginDto {
    private UUID id;
    private String email;
    private String password;
    private String name;
    private String role;
}
