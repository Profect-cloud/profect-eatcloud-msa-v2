#!/bin/bash

# 🚀 EatCloud MSA 전체 서비스 + Fluent Bit 로깅 배포 스크립트
# 
# 이 스크립트는 다음을 배포합니다:
# 1. 모든 MSA 서비스 (7개 비즈니스 서비스)
# 2. Fluent Bit 사이드카
# 3. 통합 Ingress
# 4. 로깅 검증

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
CLUSTER_NAME="eatcloud"
AWS_REGION="ap-northeast-2"
AWS_ACCOUNT_ID="536580887516"
ECR_BASE="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/eatcloud"

# MSA 서비스 목록 (API Gateway, Eureka Server 제외)
SERVICES=(
    "auth-service:8083"
    "admin-service:8081"
    "customer-service:8082" 
    "store-service:8084"
    "order-service:8085"
    "payment-service:8086"
    "manager-service:8087"
)

echo -e "${BLUE}🚀 EatCloud MSA 전체 서비스 + Fluent Bit 로깅 배포를 시작합니다...${NC}"
echo -e "${YELLOW}📋 배포할 서비스: ${#SERVICES[@]}개${NC}"
for service in "${SERVICES[@]}"; do
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

check_and_build_image() {
    local service_info=$1
    local service_name=$(echo $service_info | cut -d':' -f1)
    local port=$(echo $service_info | cut -d':' -f2)
    
    echo ""
    echo -e "${BLUE}🔍 ${service_name} 이미지 확인 중...${NC}"
    
    # ECR 이미지 존재 확인
    if aws ecr describe-images --region ${AWS_REGION} --repository-name "eatcloud/${service_name}" --image-ids imageTag=latest >/dev/null 2>&1; then
        echo -e "${GREEN}✅ ${service_name} 이미지가 ECR에 존재합니다.${NC}"
        
        # 최신 업데이트 날짜 확인
        image_date=$(aws ecr describe-images --region ${AWS_REGION} --repository-name "eatcloud/${service_name}" --image-ids imageTag=latest --query 'imageDetails[0].imagePushedAt' --output text)
        echo -e "  📅 마지막 업데이트: ${image_date}"
        
        echo -e "${YELLOW}❓ ${service_name} 이미지를 다시 빌드하시겠습니까? (y/N)${NC}"
        read -r response
        if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
            build_service_image $service_name
        fi
    else
        echo -e "${YELLOW}⚠️  ${service_name} 이미지가 ECR에 없습니다. 빌드를 진행합니다...${NC}"
        build_service_image $service_name
    fi
}

build_service_image() {
    local service_name=$1
    
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
}

# 1. Prerequisites 확인
print_section "1. Prerequisites 확인"

echo -e "${YELLOW}🔍 필수 도구 확인 중...${NC}"
for cmd in kubectl aws docker; do
    if ! command -v $cmd &> /dev/null; then
        echo -e "${RED}❌ $cmd이 설치되지 않았습니다.${NC}"
        exit 1
    else
        echo -e "${GREEN}✅ $cmd 설치 확인${NC}"
    fi
done

# AWS 인증 확인
echo -e "${YELLOW}🔐 AWS 인증 확인 중...${NC}"
if aws sts get-caller-identity >/dev/null 2>&1; then
    account_id=$(aws sts get-caller-identity --query Account --output text)
    echo -e "${GREEN}✅ AWS 인증 성공 (Account: ${account_id})${NC}"
else
    echo -e "${RED}❌ AWS 인증에 실패했습니다.${NC}"
    exit 1
fi

# EKS 컨텍스트 확인
echo -e "${YELLOW}🔗 EKS 컨텍스트 확인 중...${NC}"
current_context=$(kubectl config current-context 2>/dev/null || echo "none")
if [[ $current_context != *"$CLUSTER_NAME"* ]]; then
    echo -e "${YELLOW}⚠️  EKS 컨텍스트를 설정합니다...${NC}"
    aws eks update-kubeconfig --region ${AWS_REGION} --name ${CLUSTER_NAME}
fi

ask_continue

# 2. ECR 로그인 및 이미지 확인
print_section "2. ECR 로그인 및 이미지 확인"

echo -e "${YELLOW}🐳 ECR 로그인 중...${NC}"
aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_BASE}

echo -e "${YELLOW}🔍 모든 서비스 이미지 확인 중...${NC}"
for service in "${SERVICES[@]}"; do
    check_and_build_image $service
done

ask_continue

# 3. Fluent Bit 설정 배포
print_section "3. Fluent Bit 설정 배포"

echo -e "${YELLOW}📋 Fluent Bit ConfigMap 및 RBAC 배포 중...${NC}"
kubectl apply -f 01-fluent-bit-configmap.yaml
kubectl apply -f 02-fluent-bit-rbac.yaml

echo -e "${GREEN}✅ Fluent Bit 설정 완료${NC}"

ask_continue

# 4. 전체 서비스 배포 파일 생성
print_section "4. 전체 서비스 배포 파일 생성"

echo -e "${YELLOW}📝 전체 서비스 배포 YAML 생성 중...${NC}"

# 통합 배포 파일 생성
generate_all_services_yaml() {
    cat > 06-all-services-deployment.yaml << 'EOF'
# EatCloud MSA 전체 서비스 + Fluent Bit 사이드카 배포
# 각 서비스마다 Fluent Bit이 사이드카로 배포되어 로그를 수집합니다.

EOF

    for service_info in "${SERVICES[@]}"; do
        service_name=$(echo $service_info | cut -d':' -f1)
        port=$(echo $service_info | cut -d':' -f2)
        
        cat >> 06-all-services-deployment.yaml << EOF
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
          failureThreshold: 3
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
        env:
        - name: AWS_REGION
          value: "${AWS_REGION}"
        - name: AWS_DEFAULT_REGION
          value: "${AWS_REGION}"
        volumeMounts:
        - name: fluent-bit-config
          mountPath: /fluent-bit/etc
        - name: app-logs
          mountPath: /var/log/app
          readOnly: true
        - name: fluent-bit-db
          mountPath: /fluent-bit/tail
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
}

generate_all_services_yaml

echo -e "${GREEN}✅ 전체 서비스 배포 YAML 생성 완료${NC}"

ask_continue

# 5. 전체 서비스 배포
print_section "5. 전체 서비스 배포"

echo -e "${YELLOW}🚀 전체 서비스 배포 중...${NC}"
kubectl apply -f 06-all-services-deployment.yaml

echo -e "${YELLOW}⏳ 모든 Pod가 시작될 때까지 대기 중...${NC}"
for service_info in "${SERVICES[@]}"; do
    service_name=$(echo $service_info | cut -d':' -f1)
    echo -e "${BLUE}🔄 ${service_name} Pod 대기 중...${NC}"
    kubectl wait --for=condition=Ready pod -l app=${service_name} -n ${NAMESPACE} --timeout=300s || true
done

ask_continue

# 6. 통합 Ingress 생성
print_section "6. 통합 Ingress 생성"

echo -e "${YELLOW}🌐 통합 Ingress 생성 중...${NC}"

cat > 07-all-services-ingress.yaml << EOF
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: eatcloud-all-services-ingress
  namespace: ${NAMESPACE}
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internal
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/group.name: eatcloud-internal
    alb.ingress.kubernetes.io/group.order: '100'
    alb.ingress.kubernetes.io/load-balancer-name: eatcloud-internal-alb
    alb.ingress.kubernetes.io/subnets: subnet-0c66ca1fea24116a5,subnet-029b4e47d0be0c4b5
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTP":80}]'
    alb.ingress.kubernetes.io/healthcheck-path: /actuator/health
    alb.ingress.kubernetes.io/healthcheck-interval-seconds: '30'
    alb.ingress.kubernetes.io/healthcheck-timeout-seconds: '5'
    alb.ingress.kubernetes.io/healthy-threshold-count: '2'
    alb.ingress.kubernetes.io/unhealthy-threshold-count: '3'
spec:
  rules:
  - http:
      paths:
EOF

for service_info in "${SERVICES[@]}"; do
    service_name=$(echo $service_info | cut -d':' -f1)
    
    # 서비스별 경로 매핑
    case $service_name in
        "auth-service")
            path="/auth"
            ;;
        "admin-service")
            path="/admin"
            ;;
        "customer-service")
            path="/customer"
            ;;
        "store-service")
            path="/store"
            ;;
        "order-service")
            path="/order"
            ;;
        "payment-service")
            path="/payment"
            ;;
        "manager-service")
            path="/manager"
            ;;
        *)
            path="/${service_name}"
            ;;
    esac
    
    cat >> 07-all-services-ingress.yaml << EOF
      # ${service_name}
      - path: ${path}
        pathType: Prefix
        backend:
          service:
            name: ${service_name}
            port:
              number: 80
      # ${service_name} Metrics
      - path: /metrics/${service_name}
        pathType: Prefix
        backend:
          service:
            name: ${service_name}
            port:
              number: 2020
EOF
done

kubectl apply -f 07-all-services-ingress.yaml

echo -e "${GREEN}✅ 통합 Ingress 배포 완료${NC}"

ask_continue

# 7. 배포 상태 확인 및 로깅 검증
print_section "7. 배포 상태 확인 및 로깅 검증"

echo -e "${YELLOW}📊 전체 리소스 상태 확인:${NC}"
kubectl get pods,svc,ingress -n ${NAMESPACE}

echo ""
echo -e "${YELLOW}🔍 각 서비스별 상태 확인:${NC}"
for service_info in "${SERVICES[@]}"; do
    service_name=$(echo $service_info | cut -d':' -f1)
    echo ""
    echo -e "${BLUE}📦 ${service_name} 상태:${NC}"
    
    # Pod 상태 확인
    pod_status=$(kubectl get pod -l app=${service_name} -n ${NAMESPACE} -o jsonpath='{.items[0].status.phase}' 2>/dev/null || echo "NOT_FOUND")
    if [ "$pod_status" = "Running" ]; then
        echo -e "  상태: ${GREEN}${pod_status}${NC}"
    else
        echo -e "  상태: ${RED}${pod_status}${NC}"
    fi
    
    # Fluent Bit 헬스체크
    pod_name=$(kubectl get pod -l app=${service_name} -n ${NAMESPACE} -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    if [ -n "$pod_name" ]; then
        echo -e "  Pod 이름: ${pod_name}"
        
        # Fluent Bit 헬스체크
        if kubectl exec ${pod_name} -n ${NAMESPACE} -c fluent-bit -- curl -s http://localhost:2020/api/v1/health >/dev/null 2>&1; then
            echo -e "  Fluent Bit: ${GREEN}✅ 정상${NC}"
        else
            echo -e "  Fluent Bit: ${RED}❌ 오류${NC}"
        fi
        
        # 로그 파일 확인
        echo -e "  로그 파일:"
        if kubectl exec ${pod_name} -n ${NAMESPACE} -c ${service_name} -- ls -la /var/log/app/ 2>/dev/null | grep -E "\.log$"; then
            echo -e "    ${GREEN}✅ 로그 파일 생성됨${NC}"
        else
            echo -e "    ${YELLOW}⚠️  로그 파일 확인 필요${NC}"
        fi
    fi
done

# 8. 로깅 검증을 위한 테스트 요청
print_section "8. 로깅 검증을 위한 테스트 요청"

echo -e "${YELLOW}🧪 로그 생성을 위한 테스트 요청 실행 중...${NC}"

# ALB 주소 확인
alb_address=$(kubectl get ingress eatcloud-all-services-ingress -n ${NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "대기 중...")
echo -e "${BLUE}🔗 ALB 주소: ${alb_address}${NC}"

if [ "$alb_address" != "대기 중..." ]; then
    echo -e "${YELLOW}📡 각 서비스 헬스체크 테스트 중...${NC}"
    for service_info in "${SERVICES[@]}"; do
        service_name=$(echo $service_info | cut -d':' -f1)
        
        case $service_name in
            "auth-service") path="/auth" ;;
            "admin-service") path="/admin" ;;
            "customer-service") path="/customer" ;;
            "store-service") path="/store" ;;
            "order-service") path="/order" ;;
            "payment-service") path="/payment" ;;
            "manager-service") path="/manager" ;;
        esac
        
        echo -e "  📡 ${service_name} 테스트 중..."
        curl -s -o /dev/null -w "    응답코드: %{http_code}\n" "http://${alb_address}${path}/actuator/health" || echo -e "    ${RED}❌ 연결 실패${NC}"
    done
else
    echo -e "${YELLOW}⚠️  ALB가 아직 준비되지 않았습니다. 몇 분 후 다시 시도해주세요.${NC}"
fi

# 9. 완료 및 다음 단계
print_section "9. 배포 완료"

echo -e "${GREEN}🎉 EatCloud MSA 전체 서비스 + Fluent Bit 로깅 시스템 배포가 완료되었습니다!${NC}"
echo ""

echo -e "${BLUE}📋 배포된 서비스 (${#SERVICES[@]}개):${NC}"
for service_info in "${SERVICES[@]}"; do
    service_name=$(echo $service_info | cut -d':' -f1)
    echo -e "  ✅ ${service_name}"
done

echo ""
echo -e "${BLUE}🔗 접속 정보:${NC}"
echo -e "  • ALB 주소: ${alb_address}"
echo -e "  • Auth Service: http://${alb_address}/auth"
echo -e "  • Admin Service: http://${alb_address}/admin"
echo -e "  • Customer Service: http://${alb_address}/customer"
echo -e "  • Store Service: http://${alb_address}/store"
echo -e "  • Order Service: http://${alb_address}/order"
echo -e "  • Payment Service: http://${alb_address}/payment"
echo -e "  • Manager Service: http://${alb_address}/manager"

echo ""
echo -e "${BLUE}🔍 로깅 검증 명령어:${NC}"
echo -e "  • 전체 Pod 상태: kubectl get pods -n ${NAMESPACE}"
echo -e "  • 특정 서비스 로그: kubectl logs -n ${NAMESPACE} <pod-name> -c <service-name>"
echo -e "  • Fluent Bit 로그: kubectl logs -n ${NAMESPACE} <pod-name> -c fluent-bit"
echo -e "  • 로그 파일 확인: kubectl exec -n ${NAMESPACE} <pod-name> -c <service-name> -- ls -la /var/log/app/"

echo ""
echo -e "${BLUE}📊 Kinesis Stream 확인:${NC}"
streams=("eatcloud-stateful-logs" "eatcloud-stateless-logs" "eatcloud-recommendation-events")
for stream in "${streams[@]}"; do
    status=$(aws kinesis describe-stream --stream-name ${stream} --region ${AWS_REGION} --query 'StreamDescription.StreamStatus' --output text 2>/dev/null || echo "NOT_FOUND")
    if [ "$status" = "ACTIVE" ]; then
        echo -e "  • ${stream}: ${GREEN}${status}${NC}"
    else
        echo -e "  • ${stream}: ${RED}${status}${NC}"
    fi
done

echo ""
echo -e "${BLUE}🔧 문제 해결:${NC}"
echo -e "  • 자동 진단: ./troubleshoot.sh"
echo -e "  • 로그 생성 테스트: 각 서비스 API 호출"
echo -e "  • Kinesis 데이터 확인: AWS 콘솔에서 Stream 모니터링"

echo ""
echo -e "${YELLOW}📝 다음 단계:${NC}"
echo -e "  1. 각 서비스 API 테스트로 로그 생성"
echo -e "  2. Fluent Bit 메트릭 확인"
echo -e "  3. Kinesis Stream에 데이터 유입 확인"
echo -e "  4. 추천 이벤트 활성화 테스트"

echo ""
echo -e "${GREEN}✨ 모든 MSA 서비스가 Fluent Bit와 함께 성공적으로 배포되었습니다!${NC}"
