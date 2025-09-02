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
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
		this.userDetailsService = userDetailsService;
	}

	public String createToken(UUID id, String type) {
		Date now = new Date();
		Date expiryDate = new Date(now.getTime() + tokenValidity);

		List<String> roles = switch (type.toLowerCase()) {
			case "customer" -> Arrays.asList("CUSTOMER");
			case "manager" -> Arrays.asList("MANAGER");
			case "admin" -> Arrays.asList("ADMIN");
			default -> Arrays.asList("USER");
		};

		return Jwts.builder()
			.setSubject(String.valueOf(id))
			.claim("type", type)
			.claim("roles", roles)
			.setIssuedAt(now)
			.setExpiration(expiryDate)
			.signWith(secretKey)
			.compact();
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
		Claims claims = Jwts.parserBuilder()
			.setSigningKey(secretKey)
			.build()
			.parseClaimsJws(token)
			.getBody();
		return UUID.fromString(claims.getSubject());

	}

	public String getTypeFromToken(String token) {
		Claims claims = Jwts.parserBuilder()
			.setSigningKey(secretKey)
			.build()
			.parseClaimsJws(token)
			.getBody();
		return claims.get("type", String.class);
	}

	public boolean validateToken(String token) {
		try {
			Claims claims = Jwts.parserBuilder()
				.setSigningKey(secretKey)
				.setAllowedClockSkewSeconds(30)
				.build()
				.parseClaimsJws(token)
				.getBody();

			Date expiration = claims.getExpiration();
			Date now = new Date();

			return expiration.getTime() - now.getTime() >= refreshThreshold;
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
		Claims claims = Jwts.parserBuilder()
				.setSigningKey(secretKey)
				.build()
				.parseClaimsJws(token)
				.getBody();
		return claims.getExpiration().getTime();
	}
}

