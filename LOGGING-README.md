# EatCloud MSA 로깅 시스템

이 문서는 EatCloud MSA 프로젝트에 적용된 통합 로깅 시스템에 대한 가이드입니다.

## 📋 목차

- [개요](#개요)
- [구성 요소](#구성-요소)
- [설치 및 설정](#설치-및-설정)
- [사용법](#사용법)
- [MDC (Mapped Diagnostic Context)](#mdc-mapped-diagnostic-context)
- [AOP 로깅](#aop-로깅)
- [Kafka 로깅](#kafka-로깅)
- [로그 레벨 관리](#로그-레벨-관리)
- [모니터링](#모니터링)
- [트러블슈팅](#트러블슈팅)

## 개요

EatCloud MSA 로깅 시스템은 다음과 같은 기능을 제공합니다:

- **통합 로그 관리**: 모든 마이크로서비스에서 일관된 로그 형식
- **분산 추적**: Request ID를 통한 요청 추적
- **컨텍스트 정보**: MDC를 통한 사용자, 서비스 정보 포함
- **성능 모니터링**: AOP를 통한 메서드 실행 시간 측정
- **구조화된 로그**: JSON 형태의 로그 출력 (ELK Stack 연동 가능)
- **실시간 로그 레벨 조정**: Actuator를 통한 로그 레벨 동적 변경

## 구성 요소

### 1. Core Components

```
logging-common/
├── src/main/java/com/eatcloud/logging/
│   ├── config/LoggingConfig.java              # 로깅 설정
│   ├── mdc/MDCUtil.java                      # MDC 유틸리티
│   ├── context/RequestContext.java           # 요청 컨텍스트
│   ├── filter/LoggingFilter.java             # HTTP 요청 로깅 필터
│   ├── aspect/LoggingAspect.java             # AOP 로깅
│   ├── aspect/PerformanceAspect.java         # 성능 모니터링
│   ├── annotation/Loggable.java              # 로깅 애노테이션
│   ├── interceptor/FeignLoggingInterceptor.java  # Feign 클라이언트 로깅
│   ├── kafka/KafkaLoggingInterceptor.java    # Kafka Producer 로깅
│   ├── kafka/KafkaConsumerLoggingUtil.java   # Kafka Consumer 로깅
│   ├── exception/GlobalExceptionHandler.java # 글로벌 예외 처리
│   └── util/LoggingUtils.java                # 로깅 유틸리티
└── src/main/resources/
    └── logback-spring.xml                     # Logback 설정
```

### 2. 의존성

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'net.logstash.logback:logstash-logback-encoder:8.0'
    implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

## 설치 및 설정

### 1. 자동 설치 스크립트

```bash
# 모든 서비스에 로깅 모듈 자동 적용
./apply-logging.sh
```

### 2. 수동 설치

각 서비스의 `build.gradle`에 의존성 추가:

```gradle
dependencies {
    implementation project(':logging-common')
}
```

메인 애플리케이션 클래스에 ComponentScan 추가:

```java
@SpringBootApplication
@ComponentScan(basePackages = {"com.eatcloud.yourservice", "com.eatcloud.logging"})
public class YourServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourServiceApplication.class, args);
    }
}
```

## 사용법

### 1. 기본 로깅

```java
@Slf4j
@RestController
public class CustomerController {
    
    @GetMapping("/customers/{id}")
    @Loggable  // AOP 로깅 적용
    public CustomerDto getCustomer(@PathVariable UUID id) {
        log.info("Customer request for ID: {}", id);
        
        // 비즈니스 로직
        CustomerDto customer = customerService.findById(id);
        
        log.info("Customer found: {}", customer.getName());
        return customer;
    }
}
```

### 2. 민감한 데이터 마스킹

```java
@PostMapping("/login")
@Loggable(maskSensitiveData = true)  // 민감한 데이터 마스킹
public LoginResponse login(@RequestBody LoginRequest request) {
    log.info("Login attempt for email: {}", 
             LoggingUtils.maskSensitiveData(request.getEmail()));
    
    return authService.login(request);
}
```

### 3. 성능 측정이 포함된 로깅

```java
public class CustomerService {
    
    public CustomerDto processCustomerData(UUID customerId) {
        return LoggingUtils.executeWithLogging("processCustomerData", () -> {
            // 시간이 오래 걸리는 비즈니스 로직
            return complexBusinessLogic(customerId);
        });
    }
}
```

## MDC (Mapped Diagnostic Context)

### 자동 설정되는 컨텍스트 정보

- `requestId`: 요청 추적 ID
- `userId`: 사용자 ID (JWT에서 추출)
- `userRole`: 사용자 역할
- `clientIp`: 클라이언트 IP 주소
- `userAgent`: User-Agent 정보
- `sessionId`: 세션 ID
- `serviceName`: 서비스명
- `requestStartTime`: 요청 시작 시간

### 수동 MDC 설정

```java
// 사용자 정보 설정
MDCUtil.setUserId("user123");
MDCUtil.setUserRole("customer");

// 비즈니스 로직 실행
log.info("Processing order for user");

// MDC 정리 (자동으로 처리되지만 필요시 수동 호출)
MDCUtil.clear();
```

### 컨텍스트 정보 조회

```java
String currentRequestId = MDCUtil.getRequestId();
String currentUserId = MDCUtil.getUserId();
String serviceName = MDCUtil.getServiceName();
```

## AOP 로깅

### 1. @Loggable 애노테이션

```java
@Loggable(
    level = LogLevel.INFO,           // 로그 레벨
    logParameters = true,            // 파라미터 로깅
    logResult = true,                // 반환값 로깅
    logExecutionTime = true,         // 실행 시간 로깅
    maskSensitiveData = true         // 민감한 데이터 마스킹
)
public UserDto createUser(CreateUserRequest request) {
    // 비즈니스 로직
}
```

### 2. 자동 AOP 적용 범위

- Controller: `com.eatcloud.*.controller.*.*`
- Service: `com.eatcloud.*.service.*.*`
- Repository: `com.eatcloud.*.repository.*.*` (JPA Repository 제외)

### 3. 성능 모니터링

느린 실행 감지 (기본 1초 이상):

```
2024-01-15 10:30:45.123 WARN  [request123] [user456] PerformanceAspect - 
SLOW EXECUTION DETECTED - CustomerService.processLargeData() took 2500ms (threshold: 1000ms)
```

## Kafka 로깅

### 1. Producer 로깅

자동으로 Kafka 헤더에 MDC 정보가 전파됩니다:

```java
@Service
public class OrderEventPublisher {
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    public void publishOrderCreated(OrderCreatedEvent event) {
        // MDC 정보가 자동으로 Kafka 헤더에 추가됨
        kafkaTemplate.send("order.created", event);
    }
}
```

### 2. Consumer 로깅

```java
@KafkaListener(topics = "order.created", groupId = "customer-service")
public void handleOrderCreated(
        @Payload String eventJson,
        ConsumerRecord<String, String> record) {
    
    // Kafka 헤더에서 MDC 정보 설정
    KafkaConsumerLoggingUtil.setupMDCFromKafkaHeaders(record);
    
    Exception processingException = null;
    boolean success = false;
    
    try {
        // 비즈니스 로직 처리
        processOrderEvent(eventJson);
        success = true;
        
    } catch (Exception e) {
        processingException = e;
        throw e;
    } finally {
        // 처리 완료 로깅
        KafkaConsumerLoggingUtil.logKafkaConsumerEnd(record, success, processingException);
        
        // MDC 정리
        KafkaConsumerLoggingUtil.clearKafkaMDC();
    }
}
```

## 로그 레벨 관리

### 1. 설정 파일 기본 레벨

```properties
# application.properties
logging.level.com.eatcloud.yourservice=DEBUG
logging.level.com.eatcloud.logging=DEBUG
logging.level.org.springframework.kafka=INFO
```

### 2. 실시간 로그 레벨 변경

```bash
# 특정 패키지의 로그 레벨 변경
curl -X POST "http://localhost:8082/actuator/loggers/com.eatcloud.customerservice" \
     -H "Content-Type: application/json" \
     -d '{"configuredLevel":"DEBUG"}'

# 현재 로그 레벨 확인
curl http://localhost:8082/actuator/loggers/com.eatcloud.customerservice
```

### 3. 로그 레벨별 용도

- **TRACE**: 가장 상세한 정보 (개발 시에만)
- **DEBUG**: 디버깅 정보 (개발/테스트 환경)
- **INFO**: 일반적인 정보 (운영 환경 기본)
- **WARN**: 경고 메시지
- **ERROR**: 오류 메시지

## 모니터링

### 1. 로그 파일 위치

```
./logs/
├── auth-service.log           # 일반 로그
├── auth-service-error.log     # 에러 로그
├── customer-service.log
├── customer-service-error.log
└── ...
```

### 2. JSON 로그 형식

```json
{
  "timestamp": "2024-01-15T10:30:45.123+09:00",
  "level": "INFO",
  "logger": "com.eatcloud.customerservice.controller.CustomerController",
  "message": "Customer profile request completed",
  "service": "customer-service",
  "profile": "dev",
  "traceId": "req_1a2b3c4d5e6f",
  "userId": "user_123",
  "userRole": "customer",
  "clientIp": "192.168.1.100",
  "sessionId": "session_abc123",
  "thread": "http-nio-8082-exec-1"
}
```

### 3. Prometheus 메트릭

Actuator를 통해 로그 관련 메트릭 노출:

```
http://localhost:8082/actuator/metrics
http://localhost:8082/actuator/prometheus
```

### 4. Health Check

```bash
# 로깅 시스템 상태 확인
curl http://localhost:8082/actuator/health
```

## 트러블슈팅

### 1. 일반적인 문제

**Q: 로그에 MDC 정보가 나타나지 않음**
```
A: 다음을 확인하세요:
1. LoggingFilter가 올바르게 등록되었는지 확인
2. ComponentScan에 "com.eatcloud.logging" 패키지가 포함되었는지 확인
3. 요청이 HTTP 필터를 거치는지 확인 (Kafka Consumer는 별도 설정 필요)
```

**Q: AOP 로깅이 작동하지 않음**
```
A: 다음을 확인하세요:
1. @EnableAspectJAutoProxy 설정 확인
2. spring-boot-starter-aop 의존성 확인
3. 메서드가 public이고 프록시 가능한지 확인
```

**Q: Kafka 메시지에서 MDC 정보가 전파되지 않음**
```
A: KafkaConsumerLoggingUtil.setupMDCFromKafkaHeaders() 호출 확인
```

### 2. 로그 파일 관련

**로그 파일이 생성되지 않는 경우:**
```bash
# 로그 디렉토리 권한 확인
mkdir -p ./logs
chmod 755 ./logs
```

**로그 파일 용량 관리:**
```xml
<!-- logback-spring.xml에서 설정 -->
<maxFileSize>100MB</maxFileSize>
<maxHistory>30</maxHistory>
<totalSizeCap>3GB</totalSizeCap>
```

### 3. 성능 고려사항

**로그 성능 최적화:**
```properties
# Async 로깅 사용 (이미 적용됨)
# 로그 레벨을 적절히 설정
logging.level.org.springframework=WARN
logging.level.org.hibernate=WARN
```

**MDC 메모리 누수 방지:**
```java
// 긴 작업 후에는 반드시 MDC 정리
try {
    // 비즈니스 로직
} finally {
    MDCUtil.clear();
}
```

### 4. 디버깅 도구

**로그 추적:**
```bash
# 특정 Request ID로 로그 검색
grep "req_1a2b3c4d5e6f" ./logs/*.log

# 특정 사용자의 모든 로그 검색
grep "user_123" ./logs/*.log

# 에러 로그만 확인
tail -f ./logs/*-error.log
```

**실시간 로그 모니터링:**
```bash
# 모든 서비스 로그 실시간 확인
tail -f ./logs/*.log

# JSON 로그 파싱
tail -f ./logs/customer-service.log | jq .
```

## Best Practices

### 1. 로그 메시지 작성

```java
// ✅ Good
log.info("Customer order created: orderId={}, customerId={}, amount={}", 
         order.getId(), order.getCustomerId(), order.getAmount());

// ❌ Bad
log.info("Order created: " + order.toString());
```

### 2. 예외 로깅

```java
// ✅ Good
try {
    processOrder(order);
} catch (OrderProcessingException e) {
    log.error("Order processing failed: orderId={}, error={}", 
             order.getId(), e.getMessage(), e);
    throw e;
}

// ❌ Bad
log.error("Error: " + e.getMessage());
```

### 3. 민감한 정보 처리

```java
// ✅ Good
log.info("Login attempt for user: {}", 
         LoggingUtils.maskSensitiveData(request.getEmail()));

// ❌ Bad
log.info("Login attempt: {}", request); // 비밀번호 노출 위험
```

---

더 자세한 정보나 문의사항이 있으시면 개발팀에 문의하세요.
