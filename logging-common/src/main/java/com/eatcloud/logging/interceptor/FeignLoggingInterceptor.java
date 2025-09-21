package com.eatcloud.logging.interceptor;

import com.eatcloud.logging.mdc.MDCUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FeignLoggingInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // MDC에서 Request ID 가져와서 헤더에 전파
        String requestId = MDCUtil.getRequestId();
        if (requestId != null) {
            template.header("X-Request-ID", requestId);
        }
        
        String userId = MDCUtil.getUserId();
        if (userId != null) {
            template.header("X-User-ID", userId);
        }
        
        String serviceName = MDCUtil.getServiceName();
        if (serviceName != null) {
            template.header("X-Source-Service", serviceName);
        }
        
        log.debug("FEIGN REQUEST - {} {} with headers: {}", 
                template.method(), template.url(), template.headers());
    }
}
