package com.eatcloud.logging.filter;

import com.eatcloud.logging.context.RequestContext;
import com.eatcloud.logging.mdc.MDCUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@Order(1)
public class LoggingFilter implements Filter {

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        long startTime = System.currentTimeMillis();
        
        try {
            // MDC 설정
            setupMDC(httpRequest, startTime);
            
            log.info("REQUEST START - {} {} from {}",
                    httpRequest.getMethod(),
                    httpRequest.getRequestURI(),
                    getClientIpAddress(httpRequest));
            
            // 다음 필터 체인 실행
            chain.doFilter(request, response);
            
        } finally {
            // 응답 로깅
            long duration = System.currentTimeMillis() - startTime;
            log.info("REQUEST END - {} {} - Status: {} - Duration: {}ms",
                    httpRequest.getMethod(),
                    httpRequest.getRequestURI(),
                    httpResponse.getStatus(),
                    duration);
            
            // MDC 정리
            MDCUtil.clear();
        }
    }
    
    private void setupMDC(HttpServletRequest request, long startTime) {
        // Request ID 생성 또는 헤더에서 가져오기
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null) {
            requestId = MDCUtil.generateRequestId();
        }
        
        // 사용자 정보 (JWT 토큰에서 추출하거나 헤더에서 가져오기)
        String userId = request.getHeader("X-User-ID");
        String userRole = request.getHeader("X-User-Role");
        String sessionId = request.getSession(false) != null ? request.getSession().getId() : null;
        
        RequestContext context = RequestContext.builder()
                .requestId(requestId)
                .userId(userId)
                .userRole(userRole)
                .clientIp(getClientIpAddress(request))
                .userAgent(request.getHeader("User-Agent"))
                .sessionId(sessionId)
                .serviceName(serviceName)
                .requestStartTime(startTime)
                .build();
        
        MDCUtil.setRequestContext(context);
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
