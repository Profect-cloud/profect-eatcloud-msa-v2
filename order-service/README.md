# Order Service

주문 관리 마이크로서비스

## 주요 기능

- 장바구니 관리
- 주문 생성 및 관리
- 주문 상태 추적
- 리뷰 관리
- 분산 트랜잭션 처리 (Saga 패턴)

## 기술 스택

- Spring Boot 3.5.3
- Java 21
- PostgreSQL
- Redisson (분산락 및 분산 트랜잭션)
- Spring Cloud Netflix Eureka
- Spring Data JPA

## Redisson 도입 배경

### 이전 구현 (Spring Data Redis + Lettuce)
- 단순한 SET NX 명령을 사용한 분산락
- 장바구니→주문 변환 시 중복 방지용
- 단일 리소스에 대한 락만 처리 가능

### 현재 구현 (Redisson)
- **Multi-Lock**: 여러 리소스에 대한 원자적 락 획득
- **Fair Lock**: 대기 순서 보장으로 starvation 방지
- **Watchdog**: 장시간 트랜잭션을 위한 락 자동 연장
- **Saga 패턴**: 분산 트랜잭션 오케스트레이션

## 분산 트랜잭션 처리 (Saga Pattern)

### 주문 생성 프로세스

```java
1. 분산락 획득 (customer, store, cart)
2. 장바구니 조회
3. 재고 확인 (store-service)
4. 메뉴 가격 검증
5. 포인트 차감 (customer-service) - 선택적
6. 주문 생성
7. 재고 예약 (store-service)
8. 장바구니 비우기
```

### 보상 트랜잭션

각 단계별 실패 시 자동으로 이전 단계들을 롤백:
- 재고 예약 취소
- 주문 취소
- 포인트 환불

## API 엔드포인트

### 장바구니 관리
- `GET /cart` - 장바구니 조회
- `POST /cart/items` - 장바구니에 상품 추가
- `PUT /cart/items/{menuId}` - 장바구니 상품 수정
- `DELETE /cart/items/{menuId}` - 장바구니 상품 삭제
- `DELETE /cart` - 장바구니 비우기

### 주문 관리
- `POST /orders/create-from-cart` - 장바구니에서 주문 생성 (Saga 패턴)
- `GET /orders` - 주문 목록 조회
- `GET /orders/{orderId}` - 주문 상세 조회
- `PUT /orders/{orderId}/status` - 주문 상태 업데이트

### 리뷰 관리
- `POST /reviews` - 리뷰 작성
- `GET /reviews/order/{orderId}` - 주문별 리뷰 조회

## 설정

### application.properties

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/order_db
spring.datasource.username=order_user
spring.datasource.password=order_pass

# Redis (Redisson)
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.password=

# Eureka
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```

## 주요 클래스 구조

### 서비스 계층
- `OrderService` - 주문 관리 핵심 로직
- `SagaOrchestrator` - Saga 패턴 오케스트레이터
- `DistributedLockService` - Redisson 기반 분산락 서비스
- `CartService` - 장바구니 관리
- `ExternalApiService` - 외부 서비스 통신

### 분산락 사용 예제

```java
// 단일 락
distributedLockService.executeWithLock(
    "resource-key",
    10,  // 대기 시간 (초)
    30,  // 유지 시간 (초)
    TimeUnit.SECONDS,
    () -> {
        // 락으로 보호되는 작업
        return result;
    }
);

// 다중 락 (분산 트랜잭션)
String[] lockKeys = {"key1", "key2", "key3"};
distributedLockService.executeWithMultiLock(
    lockKeys,
    10, 30, TimeUnit.SECONDS,
    () -> {
        // 여러 리소스에 대한 작업
        return result;
    }
);
```

## 모니터링

### 로그 레벨
- Saga 트랜잭션: INFO
- 분산락 획득/해제: DEBUG
- 보상 트랜잭션: WARN/ERROR

### 주요 메트릭
- 락 획득 성공/실패율
- Saga 트랜잭션 성공/실패율
- 보상 트랜잭션 실행 횟수

## 트러블슈팅

### 분산락 타임아웃
- 현상: "Failed to acquire lock" 에러
- 원인: 동시 요청이 많거나 락 유지 시간이 너무 김
- 해결: 
  - 락 대기 시간 증가
  - 작업 최적화로 락 유지 시간 단축
  - Fair Lock 사용으로 순서 보장

### Saga 보상 실패
- 현상: "MANUAL_INTERVENTION_REQUIRED" 로그
- 원인: 네트워크 장애 또는 외부 서비스 장애
- 해결: 
  - 실패한 보상 트랜잭션 수동 처리
  - Dead Letter Queue 구현 고려
  - Circuit Breaker 패턴 적용

## 향후 개선 사항

1. **이벤트 소싱**
   - 현재: 동기식 Saga 오케스트레이션
   - 개선: 이벤트 기반 Choreography Saga

2. **Circuit Breaker**
   - 외부 서비스 장애 시 빠른 실패 처리

3. **분산 추적**
   - Sleuth + Zipkin으로 트랜잭션 추적

4. **메시지 큐**
   - RabbitMQ/Kafka로 비동기 처리

## 개발자 가이드

### 로컬 실행
```bash
# Redis 실행
docker run -d -p 6379:6379 redis

# PostgreSQL 실행
docker run -d -p 5432:5432 \
  -e POSTGRES_DB=order_db \
  -e POSTGRES_USER=order_user \
  -e POSTGRES_PASSWORD=order_pass \
  postgres

# 서비스 실행
./gradlew bootRun
```

### 테스트
```bash
# 단위 테스트
./gradlew test

# 통합 테스트
./gradlew integrationTest
```

## 라이선스

© 2024 EatCloud. All rights reserved.
