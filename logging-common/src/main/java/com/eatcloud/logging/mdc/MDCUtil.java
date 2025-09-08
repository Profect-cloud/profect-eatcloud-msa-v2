package com.eatcloud.logging.mdc;

import com.eatcloud.logging.context.RequestContext;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MDCUtil {
    
    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String USER_ROLE = "userRole";
    public static final String CLIENT_IP = "clientIp";
    public static final String USER_AGENT = "userAgent";
    public static final String SESSION_ID = "sessionId";
    public static final String SERVICE_NAME = "serviceName";
    public static final String REQUEST_START_TIME = "requestStartTime";
    
    public static void setRequestContext(RequestContext context) {
        MDC.put(REQUEST_ID, context.getRequestId());
        MDC.put(USER_ID, context.getUserId());
        MDC.put(USER_ROLE, context.getUserRole());
        MDC.put(CLIENT_IP, context.getClientIp());
        MDC.put(USER_AGENT, context.getUserAgent());
        MDC.put(SESSION_ID, context.getSessionId());
        MDC.put(SERVICE_NAME, context.getServiceName());
        MDC.put(REQUEST_START_TIME, String.valueOf(context.getRequestStartTime()));
    }
    
    public static void setRequestId(String requestId) {
        MDC.put(REQUEST_ID, requestId != null ? requestId : generateRequestId());
    }
    
    public static void setUserId(String userId) {
        MDC.put(USER_ID, userId);
    }
    
    public static void setUserRole(String userRole) {
        MDC.put(USER_ROLE, userRole);
    }
    
    public static void setClientIp(String clientIp) {
        MDC.put(CLIENT_IP, clientIp);
    }
    
    public static void setUserAgent(String userAgent) {
        MDC.put(USER_AGENT, userAgent);
    }
    
    public static void setSessionId(String sessionId) {
        MDC.put(SESSION_ID, sessionId);
    }
    
    public static void setServiceName(String serviceName) {
        MDC.put(SERVICE_NAME, serviceName);
    }
    
    public static void setRequestStartTime(long startTime) {
        MDC.put(REQUEST_START_TIME, String.valueOf(startTime));
    }
    
    public static String getRequestId() {
        return MDC.get(REQUEST_ID);
    }
    
    public static String getUserId() {
        return MDC.get(USER_ID);
    }
    
    public static String getServiceName() {
        return MDC.get(SERVICE_NAME);
    }
    
    public static String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    public static void clear() {
        MDC.clear();
    }
    
    public static void clearRequestContext() {
        MDC.remove(REQUEST_ID);
        MDC.remove(USER_ID);
        MDC.remove(USER_ROLE);
        MDC.remove(CLIENT_IP);
        MDC.remove(USER_AGENT);
        MDC.remove(SESSION_ID);
        MDC.remove(SERVICE_NAME);
        MDC.remove(REQUEST_START_TIME);
    }
}
