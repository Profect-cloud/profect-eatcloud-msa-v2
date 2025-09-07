#!/bin/bash

# EatCloud MSA v2 - Kubernetes 유틸리티 스크립트

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

NAMESPACE="eatcloud"

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

# 서비스 상태 확인
check_status() {
    log_info "EatCloud 서비스 상태 확인 중..."
    echo ""
    echo "📊 Pods:"
    kubectl get pods -n $NAMESPACE
    echo ""
    echo "🌐 Services:"
    kubectl get services -n $NAMESPACE
    echo ""
    echo "💾 PersistentVolumeClaims:"
    kubectl get pvc -n $NAMESPACE
}

# 로그 확인
check_logs() {
    if [ -z "$1" ]; then
        echo "사용법: $0 logs <service-name>"
        echo "예시: $0 logs order-service"
        return 1
    fi
    
    log_info "$1 서비스 로그 확인 중..."
    kubectl logs -f deployment/$1 -n $NAMESPACE
}

# 서비스 스케일링
scale_service() {
    if [ -z "$1" ] || [ -z "$2" ]; then
        echo "사용법: $0 scale <service-name> <replicas>"
        echo "예시: $0 scale order-service 3"
        return 1
    fi
    
    log_info "$1 서비스를 $2개 인스턴스로 스케일링 중..."
    kubectl scale deployment $1 --replicas=$2 -n $NAMESPACE
    
    if [ $? -eq 0 ]; then
        log_success "$1 서비스 스케일링 완료"
        kubectl get pods -l app=$1 -n $NAMESPACE
    else
        log_error "$1 서비스 스케일링 실패"
    fi
}

# 서비스 재시작
restart_service() {
    if [ -z "$1" ]; then
        echo "사용법: $0 restart <service-name>"
        echo "예시: $0 restart order-service"
        return 1
    fi
    
    log_info "$1 서비스 재시작 중..."
    kubectl rollout restart deployment/$1 -n $NAMESPACE
    kubectl rollout status deployment/$1 -n $NAMESPACE
    
    if [ $? -eq 0 ]; then
        log_success "$1 서비스 재시작 완료"
    else
        log_error "$1 서비스 재시작 실패"
    fi
}

# 포트 포워딩
port_forward() {
    if [ -z "$1" ]; then
        echo "사용법: $0 port-forward <service-name> [local-port:service-port]"
        echo "예시: $0 port-forward api-gateway 8080:8080"
        echo "예시: $0 port-forward order-service 8086:8080"
        return 1
    fi
    
    local service=$1
    local ports=${2:-"8080:8080"}
    
    log_info "$service 서비스 포트 포워딩 시작 (${ports})..."
    log_warning "Ctrl+C로 종료하세요"
    kubectl port-forward -n $NAMESPACE service/$service $ports
}

# 데이터베이스 접속
db_connect() {
    log_info "PostgreSQL 데이터베이스 접속 중..."
    kubectl exec -it -n $NAMESPACE statefulset/eatcloud-db -- psql -U eatcloud_user -d eatcloud_db
}

# Redis 접속
redis_connect() {
    log_info "Redis 접속 중..."
    kubectl exec -it -n $NAMESPACE deployment/eatcloud-redis -- redis-cli
}

# 클러스터 정리
cleanup() {
    log_warning "EatCloud 네임스페이스와 모든 리소스를 삭제합니다..."
    read -p "계속하시겠습니까? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        kubectl delete namespace $NAMESPACE
        log_success "클러스터 정리 완료"
    else
        log_info "클러스터 정리가 취소되었습니다."
    fi
}

# 리소스 사용량 확인
resource_usage() {
    log_info "리소스 사용량 확인 중..."
    echo ""
    echo "📊 Node 리소스 사용량:"
    kubectl top nodes
    echo ""
    echo "📦 Pod 리소스 사용량:"
    kubectl top pods -n $NAMESPACE
}

# 도움말
show_help() {
    echo "EatCloud MSA v2 - Kubernetes 유틸리티 스크립트"
    echo ""
    echo "사용법: $0 <command> [options]"
    echo ""
    echo "Commands:"
    echo "  status                    - 서비스 상태 확인"
    echo "  logs <service>           - 서비스 로그 확인"
    echo "  scale <service> <count>  - 서비스 스케일링"
    echo "  restart <service>        - 서비스 재시작"
    echo "  port-forward <service>   - 포트 포워딩"
    echo "  db-connect              - PostgreSQL 데이터베이스 접속"
    echo "  redis-connect           - Redis 접속"
    echo "  resources               - 리소스 사용량 확인"
    echo "  cleanup                 - 클러스터 정리"
    echo "  help                    - 이 도움말 표시"
    echo ""
    echo "예시:"
    echo "  $0 status"
    echo "  $0 logs order-service"
    echo "  $0 scale order-service 3"
    echo "  $0 port-forward api-gateway 8080:8080"
}

# 메인 로직
case "$1" in
    "status")
        check_status
        ;;
    "logs")
        check_logs $2
        ;;
    "scale")
        scale_service $2 $3
        ;;
    "restart")
        restart_service $2
        ;;
    "port-forward")
        port_forward $2 $3
        ;;
    "db-connect")
        db_connect
        ;;
    "redis-connect")
        redis_connect
        ;;
    "resources")
        resource_usage
        ;;
    "cleanup")
        cleanup
        ;;
    "help"|"--help"|"-h")
        show_help
        ;;
    *)
        log_error "알 수 없는 명령어: $1"
        echo ""
        show_help
        exit 1
        ;;
esac
