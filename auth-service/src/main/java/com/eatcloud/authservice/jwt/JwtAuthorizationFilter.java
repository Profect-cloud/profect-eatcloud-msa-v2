package com.eatcloud.authservice.jwt;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.eatcloud.authservice.userDetails.CustomUserDetailsService;

@Component
public class JwtAuthorizationFilter extends OncePerRequestFilter {

	private final JwtTokenProvider jwtTokenProvider;
	private final CustomUserDetailsService customUserDetailsService;

	public JwtAuthorizationFilter(JwtTokenProvider jwtTokenProvider, CustomUserDetailsService customUserDetailsService) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.customUserDetailsService = customUserDetailsService;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (path.startsWith("/api/v1/auth/")) {
			return true;
		}
		if (path.startsWith("/oauth2/") || path.startsWith("/auth/success")) {
			return true;
		}
		return false;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {

		String path = request.getServletPath();
		if (path.startsWith("/api/v1/unauth/**")) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = jwtTokenProvider.resolveToken(request);

		if (token != null) {
			try {
				if (!jwtTokenProvider.validateToken(token)) {
					UUID userId = jwtTokenProvider.getIdFromToken(token);
					String userType = jwtTokenProvider.getTypeFromToken(token);
					String newToken = jwtTokenProvider.createToken(userId, userType);
					response.setHeader("Authorization", "Bearer " + newToken);
					token = newToken;
				}

				UUID userId = jwtTokenProvider.getIdFromToken(token);
				String userType = jwtTokenProvider.getTypeFromToken(token);

				UserDetails userDetails = customUserDetailsService.loadUserByIdAndType(userId, userType);

				UsernamePasswordAuthenticationToken authentication =
						new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (ExpiredJwtException e) {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.getWriter().write("{\"message\": \"세션이 만료되었습니다. 다시 로그인하세요.\"}");
				return;
			} catch (Throwable e) {
				throw new RuntimeException(e);
			}
		}

		filterChain.doFilter(request, response);
	}

}

