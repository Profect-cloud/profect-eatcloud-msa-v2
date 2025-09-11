#!/bin/bash

# 🚀 EatCloud MSA 로깅 시스템 빠른 테스트 배포 (Kinesis 없이)
# 
# 이 스크립트는 Kinesis 없이 Fluent Bit 로그 수집 테스트를 진행합니다:
# 1. 로컬 Fluent Bit 설정 적용
# 2. 2개 서비스만 배포 (admin-service, customer-service)
# 3. 로그 파일 생성 및 Fluent Bit 수집 확인

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# 변수 정의
NAMESPACE="dev"
AWS_REGION="ap-northeast-2"
AWS_ACCOUNT_ID="536580887516"
ECR_BASE="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/eatcloud"

# 테스트용 서비스 목록 (2개만)
TEST_SERVICES=(
    "admin-service:8081"
    "customer-service:8082"
)

echo -e "${BLUE}🚀 EatCloud MSA 로깅 시스템 빠른 테스트를 시작합니다...${NC}"
echo -e "${YELLOW}📋 테스트할 서비스: ${#TEST_SERVICES[@]}개${NC}"
for service in "${TEST_SERVICES[@]}"; do
    service_name=$(echo $service | cut -d':' -f1)
    echo -e "  • ${service_name}"
done

# 헬퍼 함수들
print_section() {
    echo ""
    echo -e "${PURPLE}======================================${NC}"
    echo -e "${PURPLE} $1${NC}"
    echo -e "${PURPLE}======================================${NC}"
}

ask_continue() {
    echo ""
    echo -e "${YELLOW}❓ 계속 진행하시겠습니까? (y/N)${NC}"
    read -r response
    if [[ ! "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
        echo -e "${RED}❌ 배포를 중단합니다.${NC}"
        exit 1
    fi
}

# 1. Prerequisites 확인
print_section "1. Prerequisites 확인"

echo -e "${YELLOW}🔍 필수 도구 확인 중...${NC}"
for cmd in kubectl docker; do
    if ! command -v $cmd &> /dev/null; then
        echo -e "${RED}❌ $cmd이 설치되지 않았습니다.${NC}"
        exit 1
    else
        echo -e "${GREEN}✅ $cmd 설치 확인${NC}"
    fi
done

# Namespace 확인
echo -e "${YELLOW}📁 Namespace 확인 중...${NC}"
if ! kubectl get namespace ${NAMESPACE} >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  Namespace ${NAMESPACE}를 생성합니다...${NC}"
    kubectl create namespace ${NAMESPACE}
fi

ask_continue

# 2. 로컬 Fluent Bit 설정 적용
print_section "2. 로컬 Fluent Bit 설정 적용"

echo -e "${YELLOW}📋 로컬 Fluent Bit ConfigMap 배포 중...${NC}"
kubectl apply -f 01-fluent-bit-configmap-local.yaml

echo -e "${YELLOW}🔐 Fluent Bit RBAC 배포 중...${NC}"
kubectl apply -f 02-fluent-bit-rbac-local.yaml

echo -e "${GREEN}✅ Fluent Bit 설정 완료${NC}"

ask_continue

# 3. 필요한 ConfigMap 및 Secret 확인
print_section "3. 필요한 리소스 확인"

# ConfigMap 확인
echo -e "${YELLOW}🗂️  ConfigMap 확인 중...${NC}"
if ! kubectl get configmap complete-configmap -n ${NAMESPACE} >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  complete-configmap이 없습니다. 기본 ConfigMap을 생성합니다...${NC}"
    
    cat > temp-configmap.yaml << EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: complete-configmap
  namespace: ${NAMESPACE}
data:
  spring.profiles.active: "dev"
  spring.application.name: "eatcloud-service"
  server.port: "8080"
  logging.level.com.eatcloud: "DEBUG"
  logging.recommendation.enabled: "true"
EOF
    kubectl apply -f temp-configmap.yaml
    rm temp-configmap.yaml
fi

# Secret 확인
echo -e "${YELLOW}🔐 Secret 확인 중...${NC}"
if ! kubectl get secret app-secrets -n ${NAMESPACE} >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  app-secrets가 없습니다. 기본 Secret을 생성합니다...${NC}"
    
    kubectl create secret generic app-secrets -n ${NAMESPACE} \
        --from-literal=database.url="jdbc:h2:mem:testdb" \
        --from-literal=database.username="test" \
        --from-literal=database.password="test"
fi

ask_continue

# 4. 이미지 빌드 및 푸시
print_section "4. 이미지 빌드 및 푸시"

echo -e "${YELLOW}🐳 ECR 로그인 중...${NC}"
aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_BASE}

for service_info in "${TEST_SERVICES[@]}"; do
    service_name=$(echo $service_info | cut -d':' -f1)
    
    echo ""
    echo -e "${BLUE}🔨 ${service_name} 이미지 빌드 중...${NC}"
    
    # 서비스 디렉토리로 이동
    cd ../${service_name}
    
    # Gradle 빌드
    echo -e "${YELLOW}📦 Gradle 빌드 중...${NC}"
    ./gradlew clean bootJar
    
    # Docker 빌드
    echo -e "${YELLOW}🐳 Docker 이미지 빌드 중...${NC}"
    docker build -t eatcloud/${service_name} .
    
    # ECR 푸시
    echo -e "${YELLOW}📤 ECR에 푸시 중...${NC}"
    docker tag eatcloud/${service_name}:latest ${ECR_BASE}/${service_name}:latest
    docker push ${ECR_BASE}/${service_name}:latest
    
    # 원래 디렉토리로 복귀
    cd ../k8s-fluent-bit
    
    echo -e "${GREEN}✅ ${service_name} 이미지 빌드 및 푸시 완료${NC}"
done

ask_continue

# 5. 테스트 서비스 배포
print_section "5. 테스트 서비스 배포"

echo -e "${YELLOW}🚀 테스트 서비스 배포 파일 생성 중...${NC}"

cat > test-services-deployment.yaml << 'EOF'
# EatCloud MSA 테스트 서비스 (admin-service, customer-service)
# Fluent Bit 사이드카로 로그 수집 테스트

EOF

for service_info in "${TEST_SERVICES[@]}"; do
    service_name=$(echo $service_info | cut -d':' -f1)
    port=$(echo $service_info | cut -d':' -f2)
    
    cat >> test-services-deployment.yaml << EOF
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${service_name}
  namespace: ${NAMESPACE}
  labels:
    app: ${service_name}
    version: v1
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ${service_name}
  template:
    metadata:
      labels:
        app: ${service_name}
        version: v1
    spec:
      serviceAccountName: fluent-bit-service-account
      containers:
      # 메인 애플리케이션 컨테이너
      - name: ${service_name}
        image: ${ECR_BASE}/${service_name}:latest
        imagePullPolicy: Always
        ports:
        - containerPort: ${port}
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "dev"
        - name: LOG_PATH
          value: "/var/log/app"
        - name: SPRING_APPLICATION_NAME
          value: "${service_name}"
        # 추천 이벤트 로깅 활성화
        - name: LOGGING_RECOMMENDATION_ENABLED
          value: "true"
        envFrom:
        - configMapRef:
            name: complete-configmap
        - secretRef:
            name: app-secrets
        volumeMounts:
        - name: app-logs
          mountPath: /var/log/app
        - name: fluent-bit-logs
          mountPath: /var/log/fluent-bit
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: ${port}
          initialDelaySeconds: 90
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 5
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: ${port}
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3

      # Fluent Bit 사이드카 컨테이너
      - name: fluent-bit
        image: fluent/fluent-bit:2.2.0
        imagePullPolicy: Always
        ports:
        - containerPort: 2020
          name: http-metrics
        volumeMounts:
        - name: fluent-bit-config
          mountPath: /fluent-bit/etc
        - name: app-logs
          mountPath: /var/log/app
          readOnly: true
        - name: fluent-bit-db
          mountPath: /fluent-bit/tail
        - name: fluent-bit-logs
          mountPath: /var/log/fluent-bit
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "200m"
        livenessProbe:
          httpGet:
            path: /api/v1/health
            port: 2020
          initialDelaySeconds: 30
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /api/v1/health
            port: 2020
          initialDelaySeconds: 10
          periodSeconds: 10

      volumes:
      # 로그 파일 공유 볼륨
      - name: app-logs
        emptyDir: {}
      # Fluent Bit DB 볼륨
      - name: fluent-bit-db
        emptyDir: {}
      # Fluent Bit 출력 로그 볼륨
      - name: fluent-bit-logs
        emptyDir: {}
      # Fluent Bit 설정
      - name: fluent-bit-config
        configMap:
          name: fluent-bit-config

---
apiVersion: v1
kind: Service
metadata:
  name: ${service_name}
  namespace: ${NAMESPACE}
  labels:
    app: ${service_name}
spec:
  ports:
  - port: 80
    targetPort: ${port}
    protocol: TCP
    name: http
  - port: 2020
    targetPort: 2020
    protocol: TCP
    name: metrics
  selector:
    app: ${service_name}
  type: ClusterIP

EOF
done

echo -e "${YELLOW}🚀 테스트 서비스 배포 중...${NC}"
kubectl apply -f test-services-deployment.yaml

echo -e "${YELLOW}⏳ Pod가 시작될 때까지 대기 중...${NC}"
for service_info in "${TEST_SERVICES[@]}"; do
    service_name=$(echo $service_info | cut -d':' -f1)
    echo -e "${BLUE}🔄 ${service_name} Pod 대기 중...${NC}"
    kubectl wait --for=condition=Ready pod -l app=${service_name} -n ${NAMESPACE} --timeout=300s || true
done

ask_continue

# 6. 배포 상태 확인
print_section "6. 배포 상태 확인"

echo -e "${YELLOW}📊 전체 리소스 상태:${NC}"
kubectl get pods,svc -n ${NAMESPACE}

echo ""
echo -e "${YELLOW}🔍 서비스별 상태 확인:${NC}"
for service_info in "${TEST_SERVICES[@]}"; do
    service_name=$(echo $service_info | cut -d':' -f1)
    pod_name=$(kubectl get pod -l app=${service_name} -n ${NAMESPACE} -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    
    if [ -n "$pod_name" ]; then
        pod_status=$(kubectl get pod ${pod_name} -n ${NAMESPACE} -o jsonpath='{.status.phase}' 2>/dev/null || echo "UNKNOWN")
        echo ""
        echo -e "${BLUE}📦 ${service_name} (${pod_name}):${NC}"
        
        if [ "$pod_status" = "Running" ]; then
            echo -e "  상태: ${GREEN}${pod_status}${NC}"
            
            # Fluent Bit 헬스체크
            if kubectl exec ${pod_name} -n ${NAMESPACE} -c fluent-bit -- curl -s http://localhost:2020/api/v1/health >/dev/null 2>&1; then
                echo -e "  Fluent Bit: ${GREEN}✅ 정상${NC}"
            else
                echo -e "  Fluent Bit: ${RED}❌ 오류${NC}"
            fi
            
            # 로그 파일 확인
            echo -e "  로그 파일:"
            if kubectl exec ${pod_name} -n ${NAMESPACE} -c ${service_name} -- ls -la /var/log/app/ 2>/dev/null; then
                kubectl exec ${pod_name} -n ${NAMESPACE} -c ${service_name} -- ls -la /var/log/app/ | sed 's/^/    /'
            else
                echo -e "    ${YELLOW}⚠️  로그 디렉토리 접근 불가${NC}"
            fi
        else
            echo -e "  상태: ${RED}${pod_status}${NC}"
        fi
    else
        echo -e "${RED}❌ ${service_name} Pod를 찾을 수 없습니다.${NC}"
    fi
done

# 7. 로깅 검증
print_section "7. 로깅 검증"

echo -e "${YELLOW}🔍 로깅 검증 스크립트 실행...${NC}"
if [ -f "./verify-logging.sh" ]; then
    ./verify-logging.sh
else
    echo -e "${YELLOW}📝 verify-logging.sh를 별도로 실행해주세요.${NC}"
fi

# 8. 완료
print_section "8. 테스트 완료"

echo -e "${GREEN}🎉 EatCloud MSA 로깅 시스템 테스트 배포가 완료되었습니다!${NC}"
echo ""

echo -e "${BLUE}📋 배포된 서비스:${NC}"
for service_info in "${TEST_SERVICES[@]}"; do
    service_name=$(echo $service_info | cut -d':' -f1)
    echo -e "  ✅ ${service_name}"
done

echo ""
echo -e "${BLUE}🔍 검증 명령어:${NC}"
echo -e "  • Pod 상태: kubectl get pods -n ${NAMESPACE}"
echo -e "  • 로그 확인: kubectl logs <pod-name> -n ${NAMESPACE} -c fluent-bit"
echo -e "  • 로그 파일: kubectl exec <pod-name> -n ${NAMESPACE} -c <service-name> -- ls -la /var/log/app/"
echo -e "  • Fluent Bit 메트릭: kubectl port-forward <pod-name> -n ${NAMESPACE} 2020:2020"

echo ""
echo -e "${BLUE}📝 다음 단계:${NC}"
echo -e "  1. ./verify-logging.sh 실행하여 로깅 상세 검증"
echo -e "  2. 서비스 API 호출하여 로그 생성 테스트"
echo -e "  3. Fluent Bit 출력 로그 확인"
echo -e "  4. 확인 후 Kinesis 설정 추가"

echo ""
echo -e "${GREEN}✨ 로깅 시스템 테스트가 완료되었습니다!${NC}"

# 임시 파일 정리
rm -f test-services-deployment.yaml
