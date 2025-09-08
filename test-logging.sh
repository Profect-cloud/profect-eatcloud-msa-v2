#!/bin/bash

# 로깅 시스템 테스트 스크립트

echo "=== EatCloud MSA 로깅 시스템 테스트 ==="

# 1. 프로젝트 빌드 테스트
echo "1. 프로젝트 빌드 중..."
./gradlew clean build -x test

if [ $? -eq 0 ]; then
    echo "   ✅ 빌드 성공"
else
    echo "   ❌ 빌드 실패"
    exit 1
fi

# 2. 로그 디렉토리 생성
echo "2. 로그 디렉토리 설정..."
mkdir -p logs
chmod 755 logs
echo "   ✅ 로그 디렉토리 생성 완료"

# 3. 설정 파일 검증
echo "3. 설정 파일 검증..."

SERVICES=("auth-service" "customer-service" "order-service" "payment-service" "store-service" "admin-service" "manager-service")

for service in "${SERVICES[@]}"; do
    PROPS_FILE="$service/src/main/resources/application.properties"
    
    if [ -f "$PROPS_FILE" ]; then
        if grep -q "logging.level.com.eatcloud.logging" "$PROPS_FILE"; then
            echo "   ✅ $service - 로깅 설정 확인됨"
        else
            echo "   ⚠️  $service - 로깅 설정 누락"
        fi
    else
        echo "   ❌ $service - application.properties 파일 없음"
    fi
done

# 4. 로깅 모듈 의존성 검증
echo "4. 로깅 모듈 의존성 검증..."

for service in "${SERVICES[@]}"; do
    BUILD_FILE="$service/build.gradle"
    
    if [ -f "$BUILD_FILE" ]; then
        if grep -q "implementation project(':logging-common')" "$BUILD_FILE"; then
            echo "   ✅ $service - logging-common 의존성 확인됨"
        else
            echo "   ⚠️  $service - logging-common 의존성 누락"
        fi
    else
        echo "   ❌ $service - build.gradle 파일 없음"
    fi
done

# 5. Logback 설정 파일 확인
echo "5. Logback 설정 확인..."
if [ -f "logging-common/src/main/resources/logback-spring.xml" ]; then
    echo "   ✅ logback-spring.xml 설정 파일 존재"
else
    echo "   ❌ logback-spring.xml 설정 파일 누락"
fi

# 6. 서비스 별 JAR 파일 확인
echo "6. JAR 파일 생성 확인..."
for service in "${SERVICES[@]}"; do
    JAR_FILE="$service/build/libs/$service-0.0.1-SNAPSHOT.jar"
    
    if [ -f "$JAR_FILE" ]; then
        echo "   ✅ $service - JAR 파일 생성됨"
    else
        echo "   ⚠️  $service - JAR 파일 누락"
    fi
done

echo ""
echo "=== 테스트 완료 ==="
echo ""
echo "다음 단계:"
echo "1. 개별 서비스 실행: java -jar {service}/build/libs/{service}-0.0.1-SNAPSHOT.jar"
echo "2. 로그 확인: tail -f logs/{service}.log"
echo "3. 로그 레벨 변경: curl -X POST localhost:808x/actuator/loggers/com.eatcloud.{service} -H 'Content-Type: application/json' -d '{\"configuredLevel\":\"DEBUG\"}'"
echo "4. 헬스 체크: curl localhost:808x/actuator/health"
echo ""
echo "로깅 시스템 기능:"
echo "✅ MDC (Request ID, User ID, Service Name 등 자동 추가)"
echo "✅ AOP 로깅 (Controller, Service, Repository 메서드 자동 로깅)"
echo "✅ JSON 구조화 로그 (ELK Stack 연동 가능)"
echo "✅ 파일 로테이션 (일별, 크기별 자동 로테이션)"
echo "✅ 성능 모니터링 (느린 메서드 자동 감지)"
echo "✅ Kafka 메시지 추적 (MDC 정보 전파)"
echo "✅ 실시간 로그 레벨 조정"
echo "✅ 글로벌 예외 처리"
