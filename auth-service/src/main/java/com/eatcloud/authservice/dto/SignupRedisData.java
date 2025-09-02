package com.eatcloud.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SignupRedisData implements Serializable {
    private SignupRequestDto request;
    private String code;
}
