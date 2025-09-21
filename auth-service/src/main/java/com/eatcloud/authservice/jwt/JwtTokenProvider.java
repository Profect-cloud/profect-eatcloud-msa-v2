package com.eatcloud.authservice.jwt;

import java.security.Key;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class JwtTokenProvider {

	private final UserDetailsService userDetailsService;

	private final Key secretKey;
	private final long tokenValidity = 1000L * 60 * 60;
	private final long refreshThreshold = 1000L * 60 * 30;
	private final long refreshTokenValidityInMs = 3 * 24 * 60 * 60 * 1000L;
	private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

	public JwtTokenProvider(@Value("${jwt.secret}") String secret,
		@Qualifier("customUserDetailsService") UserDetailsService userDetailsService) {

		// JWT Secret 디버깅 로그 추가
		logger.info("JWT Secret 길이: {}", secret != null ? secret.length() : "null");
		logger.info("JWT Secret 첫 20자: {}", secret != null && secret.length() > 20 ? secret.substring(0, 20) + "..." : secret);

		try {
			this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
			logger.info("JWT SecretKey 생성 성공: {}", secretKey.getAlgorithm());
		} catch (Exception e) {
			logger.error("JWT SecretKey 생성 실패: {}", e.getMessage());
			throw e;
		}

		this.userDetailsService = userDetailsService;
	}

	public String createToken(UUID id, String type) {
		logger.debug("토큰 생성 시작 - ID: {}, Type: {}", id, type);

		Date now = new Date();
		Date expiryDate = new Date(now.getTime() + tokenValidity);

		List<String> roles = switch (type.toLowerCase()) {
			case "customer" -> Arrays.asList("CUSTOMER");
			case "manager" -> Arrays.asList("MANAGER");
			case "admin" -> Arrays.asList("ADMIN");
			default -> Arrays.asList("USER");
		};

		logger.debug("토큰 생성 정보 - Subject: {}, Type: {}, Roles: {}, IssuedAt: {}, ExpiresAt: {}",
			id, type, roles, now, expiryDate);

		try {
			String token = Jwts.builder()
				.setSubject(String.valueOf(id))
				.claim("type", type)
				.claim("roles", roles)
				.setIssuedAt(now)
				.setExpiration(expiryDate)
				.signWith(secretKey, SignatureAlgorithm.HS256)
				.compact();

			logger.debug("토큰 생성 완료 - 길이: {}, 첫 50자: {}",
				token.length(), token.length() > 50 ? token.substring(0, 50) + "..." : token);

			return token;
		} catch (Exception e) {
			logger.error("토큰 생성 실패: {}", e.getMessage(), e);
			throw new RuntimeException("JWT 토큰 생성 실패", e);
		}
	}

	public String createRefreshToken(UUID id, String role) {
		Date now = new Date();
		Date expiryDate = new Date(now.getTime() + refreshTokenValidityInMs);

		return Jwts.builder()
			.setSubject(String.valueOf(id))
			.claim("type", role)
			.setIssuedAt(now)
			.setExpiration(expiryDate)
			.signWith(secretKey, SignatureAlgorithm.HS256)
			.compact();
	}

	public UUID getIdFromToken(String token) {
		try {
			Claims claims = Jwts.parserBuilder()
				.setSigningKey(secretKey)
				.build()
				.parseClaimsJws(token)
				.getBody();
			return UUID.fromString(claims.getSubject());
		} catch (Exception e) {
			logger.error("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
			throw new IllegalArgumentException("유효하지 않은 토큰입니다.", e);
		}
	}

	public String getTypeFromToken(String token) {
		try {
			Claims claims = Jwts.parserBuilder()
				.setSigningKey(secretKey)
				.build()
				.parseClaimsJws(token)
				.getBody();
			return claims.get("type", String.class);
		} catch (Exception e) {
			logger.error("토큰에서 사용자 타입 추출 실패: {}", e.getMessage());
			throw new IllegalArgumentException("유효하지 않은 토큰입니다.", e);
		}
	}

	public boolean validateToken(String token) {
		logger.debug("토큰 검증 시작 - 길이: {}, 첫 50자: {}",
			token != null ? token.length() : "null",
			token != null && token.length() > 50 ? token.substring(0, 50) + "..." : token);

		try {
			Claims claims = Jwts.parserBuilder()
				.setSigningKey(secretKey)
				.setAllowedClockSkewSeconds(30)
				.build()
				.parseClaimsJws(token)
				.getBody();

			Date expiration = claims.getExpiration();
			Date now = new Date();

			logger.debug("토큰 파싱 성공 - Subject: {}, Type: {}, ExpiresAt: {}, Now: {}",
				claims.getSubject(), claims.get("type"), expiration, now);

			// 토큰이 아직 유효한지 확인 (만료 시간이 현재 시간보다 이후인지)
			boolean isValid = expiration.getTime() > now.getTime();
			logger.debug("토큰 유효성 결과: {}", isValid);
			return isValid;
		} catch (ExpiredJwtException e) {
			logger.warn("JWT 만료됨: {}", e.getMessage());
		} catch (UnsupportedJwtException e) {
			logger.error("지원되지 않는 JWT: {}", e.getMessage());
		} catch (MalformedJwtException e) {
			logger.error("잘못된 JWT 형식: {}", e.getMessage());
		} catch (SignatureException e) {
			logger.error("JWT 서명 오류: {}", e.getMessage());
		} catch (IllegalArgumentException e) {
			logger.warn("JWT가 비어 있음: {}", e.getMessage());
		} catch (Exception e) {
			logger.error("예상치 못한 토큰 검증 오류: {}", e.getMessage(), e);
		}
		return false;
	}

	public String resolveToken(HttpServletRequest request) {
		String bearerToken = request.getHeader("Authorization");
		if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7).trim();
		}
		return null;
	}

	public long getExpirationTime(String token) {
		try {
			Claims claims = Jwts.parserBuilder()
				.setSigningKey(secretKey)
				.build()
				.parseClaimsJws(token)
				.getBody();
			return claims.getExpiration().getTime();
		} catch (Exception e) {
			logger.error("토큰에서 만료 시간 추출 실패: {}", e.getMessage());
			throw new IllegalArgumentException("유효하지 않은 토큰입니다.", e);
		}
	}
}

