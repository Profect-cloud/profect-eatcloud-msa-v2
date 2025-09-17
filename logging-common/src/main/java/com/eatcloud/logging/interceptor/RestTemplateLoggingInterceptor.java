package com.eatcloud.logging.interceptor;

import com.eatcloud.logging.mdc.MDCUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class RestTemplateLoggingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, 
            byte[] body, 
            ClientHttpRequestExecution execution) throws IOException {
        
        // MDC에서 Request ID 가져와서 헤더에 전파
        String requestId = MDCUtil.getRequestId();
        if (requestId != null) {
            request.getHeaders().add("X-Request-ID", requestId);
        }
        
        String userId = MDCUtil.getUserId();
        if (userId != null) {
            request.getHeaders().add("X-User-ID", userId);
        }
        
        String orderId = MDCUtil.getOrderId();
        if (orderId != null) {
            request.getHeaders().add("X-Order-ID", orderId);
        }
        
        String serviceName = MDCUtil.getServiceName();
        if (serviceName != null) {
            request.getHeaders().add("X-Source-Service", serviceName);
        }
        
        log.debug("REST CLIENT REQUEST - {} {} with traceId: {}", 
                request.getMethod(), request.getURI(), requestId);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 실제 HTTP 요청 실행
            ClientHttpResponse response = execution.execute(request, body);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("REST CLIENT SUCCESS - {} {} - Status: {} - Duration: {}ms", 
                    request.getMethod(), request.getURI(), 
                    response.getStatusCode(), duration);
            
            return response;
            
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("REST CLIENT ERROR - {} {} - Duration: {}ms - Error: {}", 
                    request.getMethod(), request.getURI(), duration, e.getMessage());
            throw e;
        }
    }
}
