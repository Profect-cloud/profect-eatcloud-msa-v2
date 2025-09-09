package com.eatcloud.authservice.controller;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.eatcloud.authservice.dto.LoginRequestDto;
import com.eatcloud.authservice.dto.LoginResponseDto;
import com.eatcloud.authservice.dto.SignupRequestDto;
import com.eatcloud.authservice.service.AuthService;
import com.eatcloud.authservice.service.RefreshTokenService;
import com.eatcloud.authservice.jwt.JwtTokenProvider;
import com.eatcloud.autoresponse.core.ApiResponse;
import com.eatcloud.autoresponse.core.ApiResponseStatus;
import com.eatcloud.logging.annotation.Loggable;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "1. AuthController")
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public class AuthController {

	private final AuthService authService;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenService refreshTokenService;
	private final RedisTemplate<String, Object> redisTemplate;

	public AuthController(AuthService authService, JwtTokenProvider jwtTokenProvider, RefreshTokenService refreshTokenService, RedisTemplate<String, Object> redisTemplate) {
		this.authService = authService;
		this.jwtTokenProvider = jwtTokenProvider;
		this.refreshTokenService = refreshTokenService;
		this.redisTemplate = redisTemplate;
	}

	@Operation(summary = "로그인", description = "사용자 이메일과 비밀번호를 검증하여 AccessToken과 RefreshToken, Type을 발급합니다.")
	@PostMapping("/login")
	public ApiResponse<LoginResponseDto> login(@RequestBody LoginRequestDto requestDto) {
		LoginResponseDto response = authService.login(requestDto.getEmail(), requestDto.getPassword());
		return ApiResponse.success(response);
	}

	@Operation(summary = "회원가입 요청", description = "이메일과 비밀번호를 받아 새로운 회원 가입을 요청합니다.")
	@PostMapping("/register")
	public ApiResponse<Void> register(@RequestBody SignupRequestDto request) {
		authService.tempSignup(request);
		return ApiResponse.success();
	}

	@Operation(summary = "이메일 인증", description = "이메일에 발송된 코드로 인증하여 회원을 등록합니다.")
	@GetMapping("/confirm-email")
	public ApiResponse<Void> confirmEmail(@RequestParam String email, @RequestParam String code) {
		log.info("Email confirmation request for email: {}", email);
		authService.confirmEmail(email, code);
		log.info("Email confirmation successful for email: {}", email);
		return ApiResponse.success();
	}

	@Operation(summary = "테스트용 회원가입 (이메일 인증 없이 바로 가입)")
	@PostMapping("/register-test")
	public ApiResponse<Void> registerTest(@RequestBody SignupRequestDto request) {
		log.info("Test registration request for email: {}", request.getEmail());
		authService.signupWithoutEmailVerification(request);
		log.info("Test registration successful for email: {}", request.getEmail());
		return ApiResponse.success();
	}

	@Operation(summary = "로그아웃")
	@PostMapping("/logout")
	public ApiResponse<Void> logout(@RequestHeader("Authorization") String bearerToken) {
		String token = bearerToken.substring(7); // "Bearer " 제거
		UUID userId = jwtTokenProvider.getIdFromToken(token);
		String role = jwtTokenProvider.getTypeFromToken(token);

		log.info("Logout request for user: {}, role: {}", userId, role);

		Object user = refreshTokenService.findUserByRoleAndId(role, userId);
		if (user != null) {
			refreshTokenService.delete(user);
		}

		long expirationMillis = jwtTokenProvider.getExpirationTime(token);
		long nowMillis = System.currentTimeMillis();
		long ttlSeconds = (expirationMillis - nowMillis) / 1000;

		if (ttlSeconds > 0) {
			redisTemplate.opsForValue().set("blacklist:access:" + token, "blacklisted", ttlSeconds, TimeUnit.SECONDS);
		}

		log.info("Logout successful for user: {}", userId);
		return ApiResponse.success();
	}

	@Operation(summary = "토큰 재발급", description = "RefreshToken을 검증하고 AccessToken과 새로운 RefreshToken을 발급합니다.")
	@PostMapping("/refresh")
	public ApiResponse<LoginResponseDto> refreshToken(@RequestParam String refreshToken) {
		UUID userId = jwtTokenProvider.getIdFromToken(refreshToken);
		String role = jwtTokenProvider.getTypeFromToken(refreshToken);

		log.info("Token refresh request for user: {}, role: {}", userId, role);

		Object user = refreshTokenService.findUserByRoleAndId(role, userId);
		if (user == null || !refreshTokenService.isValid(user, refreshToken)) {
			log.warn("Invalid refresh token for user: {}", userId);
			
			long expirationSeconds = jwtTokenProvider.getExpirationTime(refreshToken) - System.currentTimeMillis();
			refreshTokenService.addToBlacklist(refreshToken, expirationSeconds);
			if (user != null) refreshTokenService.delete(user);

			return ApiResponse.of(ApiResponseStatus.UNAUTHORIZED, null);
		}

		String newAccessToken = jwtTokenProvider.createToken(userId, role);
		String newRefreshToken = jwtTokenProvider.createRefreshToken(userId, role);
		LocalDateTime expiryDate = LocalDateTime.now().plusDays(7);

		refreshTokenService.saveOrUpdateToken(user, newRefreshToken, expiryDate);

		LoginResponseDto response = LoginResponseDto.builder()
				.token(newAccessToken)
				.refreshToken(newRefreshToken)
				.type(role)
				.build();

		log.info("Token refresh successful for user: {}", userId);
		return ApiResponse.success(response);
	}
}
