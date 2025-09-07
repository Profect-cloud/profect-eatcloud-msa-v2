#!/bin/bash

# EatCloud MSA v2 - Minikube Kubernetes 로컬 배포 스크립트
# 각 서비스별 분리된 데이터베이스 구조 적용

set -e

echo "🚀 EatCloud MSA v2 Kubernetes 로컬 배포를 시작합니다..."

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 함수 정의
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Minikube 상태 확인
check_minikube() {
    log_info "Minikube 상태 확인 중..."
    
    if ! command -v minikube &> /dev/null; then
        log_error "Minikube가 설치되지 않았습니다."
        exit 1
    fi
    
    if ! minikube status | grep -q "Running"; then
        log_warning "Minikube가 실행되지 않고 있습니다. 시작 중..."
        minikube start --driver=docker --cpus=4 --memory=7g --disk-size=50g
    else
        log_success "Minikube가 실행 중입니다."
    fi
}

# Docker 이미지 빌드
build_images() {
    log_info "Docker 이미지 빌드 중..."
    
    # Minikube Docker 환경 설정
    eval $(minikube docker-env)
    
    # 각 서비스의 Docker 이미지 빌드
    services=("eureka-server" "api-gateway" "auth-service" "customer-service" "admin-service" "manager-service" "store-service" "order-service" "payment-service")
    
    for service in "${services[@]}"; do
        log_info "빌드 중: $service"
        docker build -t eatcloud/$service:latest -f $service/Dockerfile .
        if [ $? -eq 0 ]; then
            log_success "$service 이미지 빌드 완료"
        else
            log_error "$service 이미지 빌드 실패"
            exit 1
        fi
    done
}

# Kubernetes 리소스 배포
deploy_k8s() {
    log_info "Kubernetes 리소스 배포 중..."
    
    # 네임스페이스 생성
    log_info "네임스페이스 생성 중..."
    kubectl apply -f k8s/namespace/eatcloud-namespace.yaml
    
    # ConfigMaps 및 Secrets 배포
    log_info "ConfigMaps 및 Secrets 배포 중..."
    kubectl apply -f k8s/configmaps/complete-configmap.yaml
    kubectl apply -f k8s/configmaps/database-configmap.yaml
    
    # 공통 인프라 구성 요소 배포 (Redis, Kafka)
    log_info "공통 인프라 구성 요소 배포 중..."
    kubectl apply -f k8s/infrastructure/redis.yaml
    kubectl apply -f k8s/infrastructure/kafka.yaml
    
    # Auth 서비스용 공유 데이터베이스 배포
    log_info "Auth 서비스용 공유 데이터베이스 배포 중..."
    kubectl apply -f k8s/infrastructure/postgresql.yaml
    
    # 각 서비스별 분리된 데이터베이스 배포
    log_info "서비스별 분리된 데이터베이스 배포 중..."
    kubectl apply -f k8s/infrastructure/databases/customer-db.yaml
    kubectl apply -f k8s/infrastructure/databases/admin-db.yaml
    kubectl apply -f k8s/infrastructure/databases/manager-db.yaml
    kubectl apply -f k8s/infrastructure/databases/store-db.yaml
    kubectl apply -f k8s/infrastructure/databases/order-db.yaml
    kubectl apply -f k8s/infrastructure/databases/payment-db.yaml
    
    # 데이터베이스들 Ready 상태 대기
    log_info "데이터베이스들 Ready 상태 대기 중..."
    databases=("eatcloud-db" "customer-db" "admin-db" "manager-db" "store-db" "order-db" "payment-db")
    
    for db in "${databases[@]}"; do
        log_info "$db Ready 상태 대기 중..."
        kubectl wait --for=condition=ready pod -l app=$db -n eatcloud --timeout=180s || log_warning "$db 시작 지연"
    done
    
    log_info "Redis Ready 상태 대기 중..."
    kubectl wait --for=condition=ready pod -l app=eatcloud-redis -n eatcloud --timeout=120s
    
    log_info "Kafka Ready 상태 대기 중..."
    kubectl wait --for=condition=ready pod -l app=kafka -n eatcloud --timeout=120s
    
    # 마이크로서비스 배포
    log_info "마이크로서비스 배포 중..."
    
    # Eureka Server 먼저 배포
    log_info "Eureka Server 배포 중..."
    kubectl apply -f k8s/services/eureka-server.yaml
    kubectl wait --for=condition=ready pod -l app=eureka-server -n eatcloud --timeout=180s
    
    # API Gateway 배포
    log_info "API Gateway 배포 중..."
    kubectl apply -f k8s/services/api-gateway.yaml
    kubectl wait --for=condition=ready pod -l app=api-gateway -n eatcloud --timeout=180s
    
    # 나머지 비즈니스 서비스들 단계적 배포
    log_info "Auth Service 배포 중..."
    kubectl apply -f k8s/services/auth-service.yaml
    kubectl wait --for=condition=ready pod -l app=auth-service -n eatcloud --timeout=180s || log_warning "Auth Service 시작 지연"
    
    log_info "나머지 비즈니스 서비스들 배포 중..."
    kubectl apply -f k8s/services/customer-service.yaml
    kubectl apply -f k8s/services/admin-service.yaml
    kubectl apply -f k8s/services/manager-service.yaml
    kubectl apply -f k8s/services/store-service.yaml
    kubectl apply -f k8s/services/order-service.yaml
    kubectl apply -f k8s/services/payment-service.yaml
    
    # 주요 서비스들 Ready 대기 (선택적)
    log_info "주요 서비스들 Ready 상태 확인 중..."
    services=("customer-service" "admin-service" "manager-service" "store-service" "order-service" "payment-service")
    
    for service in "${services[@]}"; do
        kubectl wait --for=condition=ready pod -l app=$service -n eatcloud --timeout=60s || log_warning "$service 시작 지연"
    done
}

# 서비스 상태 확인
check_status() {
    log_info "배포된 서비스 상태 확인 중..."
    
    echo ""
    echo "📊 배포된 리소스:"
    echo "===========================================" 
    kubectl get all -n eatcloud
    
    echo ""
    echo "💾 데이터베이스 상태:"
    echo "===========================================" 
    kubectl get pods -n eatcloud | grep -E "(db|redis|kafka)"
    
    echo ""
    echo "🚀 서비스 상태:"
    echo "===========================================" 
    kubectl get pods -n eatcloud | grep -E "(service|gateway|eureka)"
    
    echo ""
    echo "🔍 서비스 엔드포인트:"
    echo "===========================================" 
    
    # Minikube 서비스 URL 확인
    GATEWAY_URL=$(minikube service api-gateway -n eatcloud --url 2>/dev/null || echo "http://localhost:8080")
    echo "🌐 API Gateway: $GATEWAY_URL"
    echo "📊 Eureka Server: $GATEWAY_URL/eureka"
    echo "🔐 Auth Service: $GATEWAY_URL/auth"
    echo "👥 Customer Service: $GATEWAY_URL/customers"
    echo "⚙️ Admin Service: $GATEWAY_URL/admin"
    echo "👔 Manager Service: $GATEWAY_URL/managers"
    echo "🏪 Store Service: $GATEWAY_URL/stores"
    echo "📦 Order Service: $GATEWAY_URL/orders"
    echo "💳 Payment Service: $GATEWAY_URL/payments"
    
    echo ""
    echo "🔧 유용한 명령어:"
    echo "==========================================="
    echo "  포트 포워딩: kubectl port-forward -n eatcloud service/api-gateway 8080:8080"
    echo "  로그 확인: kubectl logs -f deployment/order-service -n eatcloud"
    echo "  DB 연결 테스트: kubectl exec -it deployment/order-db -n eatcloud -- psql -U eatcloud_user -d order_db"
    echo "  서비스 스케일링: kubectl scale deployment order-service --replicas=2 -n eatcloud"
    echo "  리소스 삭제: kubectl delete namespace eatcloud"
    
    echo ""
    echo "🗄️ 데이터베이스 구조:"
    echo "==========================================="
    echo "  Auth Service: eatcloud-db (공유)"
    echo "  Customer Service: customer-db"
    echo "  Admin Service: admin-db"
    echo "  Manager Service: manager-db"
    echo "  Store Service: store-db"
    echo "  Order Service: order-db"
    echo "  Payment Service: payment-db"
}

# 메인 실행
main() {
    check_minikube
    build_images
    deploy_k8s
    check_status
    
    log_success "🎉 EatCloud MSA v2 Kubernetes 로컬 배포가 완료되었습니다!"
    
    echo ""
    echo "💡 다음 단계:"
    echo "  1. 서비스 접속 테스트: curl $GATEWAY_URL/actuator/health"
    echo "  2. Eureka 대시보드 확인: $GATEWAY_URL/eureka"
    echo "  3. 각 서비스별 Health Check"
    echo "  4. 데이터베이스 연결 상태 확인"
    
    echo ""
    echo "🐛 문제 해결:"
    echo "  - 서비스 재시작: kubectl rollout restart deployment/<service-name> -n eatcloud"
    echo "  - 전체 정리 후 재배포: kubectl delete namespace eatcloud && ./deploy-k8s-local.sh"
}

# 스크립트 실행
main "$@"