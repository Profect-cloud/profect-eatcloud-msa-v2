package com.eatcloud.logging.context;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RequestContext {
    private String requestId;
    private String orderId;
    private String userId;
    private String userRole;
    private String clientIp;
    private String userAgent;
    private String sessionId;
    private String serviceName;
    private long requestStartTime;
}
