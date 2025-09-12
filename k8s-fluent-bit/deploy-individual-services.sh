#!/bin/bash

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ECR 설정
ECR_REGISTRY="536580887516.dkr.ecr.ap-northeast-2.amazonaws.com"
ECR_NAMESPACE="eatcloud"
AWS_REGION="ap-northeast-2"

# 서비스 포트 매핑 함수
get_service_port() {
    case $1 in
        "auth-service") echo "8081" ;;
        "customer-service") echo "8082" ;;
        "admin-service") echo "8083" ;;
        "manager-service") echo "8084" ;;
        "store-service") echo "8085" ;;
        "order-service") echo "8086" ;;
        "payment-service") echo "8087" ;;
        *) echo "" ;;
    esac
}

# 서비스 목록
AVAILABLE_SERVICES=("auth-service" "customer-service" "admin-service" "manager-service" "store-service" "order-service" "payment-service")

# 함수: 로그 출력
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')] $1${NC}"
}

error() {
    echo -e "${RED}[ERROR] $1${NC}"
}

success() {
    echo -e "${GREEN}[SUCCESS] $1${NC}"
}

warning() {
    echo -e "${YELLOW}[WARNING] $1${NC}"
}

# 함수: Docker 이미지 빌드 및 푸시
build_and_push_service() {
    local service_name=$1
    local service_port=$(get_service_port "$service_name")
    
    log "=== $service_name 빌드 및 푸시 시작 ==="
    
    # Gradle 빌드
    log "$service_name Gradle 빌드 중..."
    if ! ./gradlew :$service_name:clean :$service_name:build -x test; then
        error "$service_name Gradle 빌드 실패"
        return 1
    fi
    
    # ECR 로그인
    log "ECR 로그인 중..."
    if ! aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_REGISTRY; then
        error "ECR 로그인 실패"
        return 1
    fi
    
    # Docker 이미지 빌드 (AMD64)
    log "$service_name Docker 이미지 빌드 중... (AMD64)"
    if ! docker buildx build --platform linux/amd64 -t $service_name ./$service_name; then
        error "$service_name Docker 빌드 실패"
        return 1
    fi
    
    # 이미지 태그
    local image_tag="$ECR_REGISTRY/$ECR_NAMESPACE/$service_name:latest"
    log "$service_name 이미지 태그 중..."
    if ! docker tag $service_name:latest $image_tag; then
        error "$service_name 이미지 태그 실패"
        return 1
    fi
    
    # ECR 푸시
    log "$service_name ECR 푸시 중..."
    if ! docker push $image_tag; then
        error "$service_name ECR 푸시 실패"
        return 1
    fi
    
    success "$service_name 빌드 및 푸시 완료 ✅"
    return 0
}

# 함수: Kubernetes 배포
deploy_service() {
    local service_name=$1
    
    log "=== $service_name Kubernetes 배포 시작 ==="
    
    # 배포 파일 번호 매핑
    local deploy_file=""
    case $service_name in
        "auth-service")
            deploy_file="k8s-fluent-bit/06-auth-service-deployment.yaml"
            ;;
        "customer-service")
            deploy_file="k8s-fluent-bit/07-customer-service-deployment.yaml"
            ;;
        "admin-service")
            deploy_file="k8s-fluent-bit/05-admin-service-deployment.yaml"
            ;;
        "manager-service")
            deploy_file="k8s-fluent-bit/11-manager-service-deployment.yaml"
            ;;
        "store-service")
            deploy_file="k8s-fluent-bit/10-store-service-deployment.yaml"
            ;;
        "order-service")
            deploy_file="k8s-fluent-bit/08-order-service-deployment.yaml"
            ;;
        "payment-service")
            deploy_file="k8s-fluent-bit/09-payment-service-deployment.yaml"
            ;;
        *)
            error "알 수 없는 서비스: $service_name"
            return 1
            ;;
    esac
    
    if [[ ! -f "$deploy_file" ]]; then
        error "배포 파일을 찾을 수 없습니다: $deploy_file"
        return 1
    fi
    
    # Kubernetes 배포
    log "$service_name 배포 중..."
    if ! kubectl apply -f $deploy_file; then
        error "$service_name 배포 실패"
        return 1
    fi
    
    success "$service_name 배포 완료 ✅"
    return 0
}

# 함수: 배포 상태 확인
check_deployment_status() {
    local service_name=$1
    
    log "=== $service_name 배포 상태 확인 ==="
    
    # Pod 상태 확인 (최대 5분 대기)
    local timeout=300
    local elapsed=0
    local interval=10
    
    while [[ $elapsed -lt $timeout ]]; do
        local pod_status=$(kubectl get pods -n dev -l app=$service_name --no-headers | awk '{print $3}' | head -1)
        
        if [[ "$pod_status" == "Running" ]]; then
            success "$service_name 배포 성공! Pod가 실행 중입니다."
            
            # Pod 정보 출력
            kubectl get pods -n dev -l app=$service_name
            kubectl get svc -n dev -l app=$service_name
            
            return 0
        elif [[ "$pod_status" == "Error" ]] || [[ "$pod_status" == "CrashLoopBackOff" ]] || [[ "$pod_status" == "ImagePullBackOff" ]]; then
            error "$service_name 배포 실패: $pod_status"
            
            # 에러 로그 출력
            local pod_name=$(kubectl get pods -n dev -l app=$service_name --no-headers | awk '{print $1}' | head -1)
            if [[ -n "$pod_name" ]]; then
                warning "에러 로그:"
                kubectl logs $pod_name -c $service_name -n dev --tail=20
            fi
            
            return 1
        else
            log "$service_name 상태: $pod_status (대기 중... ${elapsed}s/${timeout}s)"
            sleep $interval
            elapsed=$((elapsed + interval))
        fi
    done
    
    warning "$service_name 배포 시간 초과 (${timeout}s)"
    return 1
}

# 함수: 개별 서비스 처리
process_service() {
    local service_name=$1
    local service_port=$(get_service_port "$service_name")
    
    echo ""
    log "🚀 $service_name (포트: $service_port) 처리 시작"
    echo "=================================================================================="
    
    # 1. 빌드 및 푸시
    if build_and_push_service "$service_name"; then
        success "✅ $service_name 이미지 준비 완료"
    else
        error "❌ $service_name 이미지 준비 실패"
        return 1
    fi
    
    # 2. 배포
    if deploy_service "$service_name"; then
        success "✅ $service_name 배포 완료"
    else
        error "❌ $service_name 배포 실패"
        return 1
    fi
    
    # 3. 상태 확인
    if check_deployment_status "$service_name"; then
        success "✅ $service_name 전체 프로세스 완료"
    else
        error "❌ $service_name 전체 프로세스 실패"
        return 1
    fi
    
    echo "=================================================================================="
    return 0
}

# 메인 실행 부분
main() {
    log "🎯 개별 서비스 배포 스크립트 시작"
    
    # 사용법 확인
    if [[ $# -eq 0 ]]; then
        echo "사용법:"
        echo "  $0 <service-name>           # 특정 서비스 배포"
        echo "  $0 all                      # 모든 서비스 배포"
        echo ""
        echo "사용 가능한 서비스:"
        for service in "${AVAILABLE_SERVICES[@]}"; do
            echo "  - $service (포트: $(get_service_port "$service"))"
        done
        exit 1
    fi
    
    local target_service=$1
    local failed_services=()
    local success_services=()
    
    if [[ "$target_service" == "all" ]]; then
        log "📋 모든 서비스 배포 시작"
        
        for service_name in "${AVAILABLE_SERVICES[@]}"; do
            if process_service "$service_name"; then
                success_services+=("$service_name")
            else
                failed_services+=("$service_name")
            fi
        done
        
    elif [[ " ${AVAILABLE_SERVICES[*]} " =~ " ${target_service} " ]]; then
        log "📋 $target_service 단일 서비스 배포 시작"
        
        if process_service "$target_service"; then
            success_services+=("$target_service")
        else
            failed_services+=("$target_service")
        fi
        
    else
        error "알 수 없는 서비스: $target_service"
        echo ""
        echo "사용 가능한 서비스:"
        for service in "${AVAILABLE_SERVICES[@]}"; do
            echo "  - $service (포트: $(get_service_port "$service"))"
        done
        exit 1
    fi
    
    # 최종 결과 출력
    echo ""
    echo "=================================================================================="
    log "🏁 배포 결과 요약"
    echo "=================================================================================="
    
    if [[ ${#success_services[@]} -gt 0 ]]; then
        success "✅ 성공한 서비스 (${#success_services[@]}개):"
        for service in "${success_services[@]}"; do
            echo "   - $service"
        done
    fi
    
    if [[ ${#failed_services[@]} -gt 0 ]]; then
        error "❌ 실패한 서비스 (${#failed_services[@]}개):"
        for service in "${failed_services[@]}"; do
            echo "   - $service"
        done
        echo ""
        warning "실패한 서비스는 다음 명령어로 다시 시도할 수 있습니다:"
        for service in "${failed_services[@]}"; do
            echo "   $0 $service"
        done
    fi
    
    echo "=================================================================================="
    
    # 전체 Pod 상태 확인
    echo ""
    log "📊 현재 Pod 상태:"
    kubectl get pods -n dev -l 'app in (auth-service,customer-service,admin-service,manager-service,store-service,order-service,payment-service)'
    
    echo ""
    log "📊 현재 Service 상태:"
    kubectl get svc -n dev -l 'app in (auth-service,customer-service,admin-service,manager-service,store-service,order-service,payment-service)'
    
    # 종료 코드 설정
    if [[ ${#failed_services[@]} -gt 0 ]]; then
        exit 1
    else
        success "🎉 모든 서비스 배포가 성공적으로 완료되었습니다!"
        exit 0
    fi
}

# 스크립트 실행
main "$@"
