package com.eatcloud.authservice.filter;

import com.eatcloud.logging.mdc.MDCUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@Order(2) // LoggingFilter 다음에 실행
public class JwtMdcFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        try {
            // Authorization 헤더에서 JWT 토큰 추출
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                // JWT 토큰에서 사용자 정보 추출 (실제 구현 시 JwtTokenProvider 사용)
                extractUserInfoFromToken(token);
            }
            
            chain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("Error processing JWT token for MDC", e);
            chain.doFilter(request, response);
        }
    }
    
    private void extractUserInfoFromToken(String token) {
        try {
            // 실제 JWT 파싱 로직은 JwtTokenProvider를 사용해야 함
            // 여기서는 예시로 헤더에서 정보를 가져오는 것으로 대체
            // 실제로는 JWT 토큰을 파싱하여 사용자 정보를 추출해야 함
            
            // 예시: JWT에서 추출한 정보를 MDC에 설정
            // Claims claims = jwtTokenProvider.getClaimsFromToken(token);
            // MDCUtil.setUserId(claims.getSubject());
            // MDCUtil.setUserRole(claims.get("role", String.class));
            
            log.debug("JWT token processed for MDC setup");
        } catch (Exception e) {
            log.debug("Failed to extract user info from JWT token: {}", e.getMessage());
        }
    }
}
