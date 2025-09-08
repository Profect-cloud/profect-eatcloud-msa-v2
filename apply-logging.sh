#!/bin/bash

# 로깅 모듈을 모든 서비스에 적용하는 스크립트

SERVICES=("customer-service" "admin-service" "manager-service" "store-service" "order-service" "payment-service" "api-gateway" "eureka-server")

echo "=== EatCloud MSA 로깅 모듈 적용 시작 ==="

for service in "${SERVICES[@]}"; do
    echo "Processing $service..."
    
    # 1. build.gradle에 logging-common 의존성 추가
    if grep -q "implementation project(':logging-common')" "$service/build.gradle"; then
        echo "  ✓ $service - logging-common dependency already exists"
    else
        echo "  + Adding logging-common dependency to $service"
        # auto-response 의존성 다음에 logging-common 추가
        sed -i.bak '/implementation project.*auto-response/a\
    implementation project(":logging-common")' "$service/build.gradle"
        rm -f "$service/build.gradle.bak"
    fi
    
    # 2. application.properties에 로깅 설정 추가
    PROPS_FILE="$service/src/main/resources/application.properties"
    if [ -f "$PROPS_FILE" ]; then
        if grep -q "logging.level.com.eatcloud.logging" "$PROPS_FILE"; then
            echo "  ✓ $service - logging configuration already exists"
        else
            echo "  + Adding logging configuration to $service"
            cat >> "$PROPS_FILE" << 'EOF'

# Logging Configuration
logging.level.com.eatcloud.logging=DEBUG
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{requestId:-}] [%X{userId:-}] %logger{36} - %msg%n
logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{requestId:-}] [%X{userId:-}] %logger{36} - %msg%n

# Logback 파일 경로
logging.file.path=./logs
logging.file.name=./logs/SERVICE_NAME.log

# Management endpoints
management.endpoints.web.exposure.include=health,info,metrics,loggers
management.endpoint.loggers.enabled=true
EOF
            # SERVICE_NAME을 실제 서비스명으로 치환
            sed -i.bak "s/SERVICE_NAME/$service/g" "$PROPS_FILE"
            rm -f "$PROPS_FILE.bak"
        fi
    fi
    
    # 3. Application 클래스에 ComponentScan 추가
    APP_CLASS=$(find "$service/src/main/java" -name "*Application.java" | head -1)
    if [ -f "$APP_CLASS" ]; then
        if grep -q "com.eatcloud.logging" "$APP_CLASS"; then
            echo "  ✓ $service - ComponentScan already configured"
        else
            echo "  + Adding ComponentScan to $service Application class"
            # import 추가
            sed -i.bak '/import org.springframework.boot.autoconfigure.SpringBootApplication;/a\
import org.springframework.context.annotation.ComponentScan;' "$APP_CLASS"
            
            # ComponentScan 애노테이션 추가
            sed -i.bak '/@SpringBootApplication/a\
@ComponentScan(basePackages = {"com.eatcloud.'${service//-/}'", "com.eatcloud.logging"})' "$APP_CLASS"
            
            rm -f "$APP_CLASS.bak"
        fi
    fi
    
    # 4. LoggingConfiguration 클래스 생성
    CONFIG_DIR="$service/src/main/java/com/eatcloud/${service//-/}/config"
    CONFIG_FILE="$CONFIG_DIR/LoggingConfiguration.java"
    
    if [ -f "$CONFIG_FILE" ]; then
        echo "  ✓ $service - LoggingConfiguration already exists"
    else
        echo "  + Creating LoggingConfiguration for $service"
        mkdir -p "$CONFIG_DIR"
        
        PACKAGE_NAME="com.eatcloud.${service//-/}"
        cat > "$CONFIG_FILE" << EOF
package ${PACKAGE_NAME}.config;

import com.eatcloud.logging.config.LoggingConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(LoggingConfig.class)
public class LoggingConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
EOF
    fi
    
    echo "  ✓ $service - 로깅 모듈 적용 완료"
    echo ""
done

echo "=== 로깅 모듈 적용 완료 ==="
echo ""
echo "추가 작업:"
echo "1. 각 서비스의 Controller, Service 클래스에 @Loggable 애노테이션 추가"
echo "2. 중요한 비즈니스 로직에 적절한 로그 메시지 추가"
echo "3. JWT 토큰 파싱 로직을 각 서비스에 맞게 구현"
echo "4. Kafka Producer/Consumer에 MDC 전파 로직 추가"
echo ""
echo "사용법:"
echo "- 로그 레벨 실시간 변경: POST /actuator/loggers/{logger-name} {\"configuredLevel\":\"DEBUG\"}"
echo "- 현재 로그 레벨 확인: GET /actuator/loggers"
echo "- 로그 파일 위치: ./logs/{service-name}.log"
