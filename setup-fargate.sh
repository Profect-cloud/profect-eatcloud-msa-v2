#!/bin/bash

# EKS Fargate 설정 스크립트 (sudo 없이)
# 기존 인프라를 건드리지 않고 Fargate만 설정

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

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

# 환경변수 설정 (기존 EKS 클러스터 정보)
export CLUSTER_NAME="eatcloud"
export ACCOUNT_ID="536580887516"
export REGION="ap-northeast-2"
export VPC_ID="vpc-0bdbee988c0d5e2cc"
export PRIVATE_SUBNET_1="subnet-029b4e47d0be0c4b5"
export PRIVATE_SUBNET_2="subnet-0c66ca1fea24116a5"

log_info "🚀 EKS Fargate 설정을 시작합니다..."
log_info "클러스터: ${CLUSTER_NAME}"
log_info "리전: ${REGION}"

# Step 1: kubeconfig 업데이트
setup_kubeconfig() {
    log_info "kubeconfig 업데이트 중..."
    aws eks update-kubeconfig --region $REGION --name $CLUSTER_NAME
    log_success "kubeconfig 업데이트 완료"
}

# Step 2: IAM 역할 확인/생성
create_iam_roles() {
    log_info "IAM 역할 확인 중..."
    
    # Fargate Pod Execution Role 확인
    if aws iam get-role --role-name EKSFargatePodExecutionRole >/dev/null 2>&1; then
        log_success "EKSFargatePodExecutionRole 이미 존재"
    else
        log_info "EKSFargatePodExecutionRole 생성 중..."
        
        cat > /tmp/fargate-trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "eks-fargate-pods.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

        aws iam create-role \
          --role-name EKSFargatePodExecutionRole \
          --assume-role-policy-document file:///tmp/fargate-trust-policy.json
        
        aws iam attach-role-policy \
          --role-name EKSFargatePodExecutionRole \
          --policy-arn arn:aws:iam::aws:policy/AmazonEKSFargatePodExecutionRolePolicy
        
        rm /tmp/fargate-trust-policy.json
        log_success "EKSFargatePodExecutionRole 생성 완료"
    fi
}

# Step 3: Namespace 생성
create_namespaces() {
    log_info "Namespace 생성 중..."
    
    namespaces=("eatcloud" "monitoring" "aws-observability")
    
    for ns in "${namespaces[@]}"; do
        if kubectl get namespace $ns >/dev/null 2>&1; then
            log_success "Namespace '$ns' 이미 존재"
        else
            kubectl create namespace $ns
            log_success "Namespace '$ns' 생성 완료"
        fi
    done
}

# Step 4: Fargate Profile 생성
create_fargate_profiles() {
    log_info "Fargate Profile 생성 중..."
    
    # eatcloud namespace용
    if aws eks describe-fargate-profile --cluster-name $CLUSTER_NAME --fargate-profile-name eatcloud-profile >/dev/null 2>&1; then
        log_success "Fargate Profile 'eatcloud-profile' 이미 존재"
    else
        log_info "Fargate Profile 'eatcloud-profile' 생성 중..."
        aws eks create-fargate-profile \
          --cluster-name $CLUSTER_NAME \
          --fargate-profile-name eatcloud-profile \
          --pod-execution-role-arn arn:aws:iam::${ACCOUNT_ID}:role/EKSFargatePodExecutionRole \
          --subnets $PRIVATE_SUBNET_1 $PRIVATE_SUBNET_2 \
          --selectors namespace=eatcloud
        log_info "eatcloud-profile 생성 요청됨 (완료까지 5-10분 소요)"
    fi
    
    # monitoring namespace용
    if aws eks describe-fargate-profile --cluster-name $CLUSTER_NAME --fargate-profile-name monitoring-profile >/dev/null 2>&1; then
        log_success "Fargate Profile 'monitoring-profile' 이미 존재"
    else
        log_info "Fargate Profile 'monitoring-profile' 생성 중..."
        aws eks create-fargate-profile \
          --cluster-name $CLUSTER_NAME \
          --fargate-profile-name monitoring-profile \
          --pod-execution-role-arn arn:aws:iam::${ACCOUNT_ID}:role/EKSFargatePodExecutionRole \
          --subnets $PRIVATE_SUBNET_1 $PRIVATE_SUBNET_2 \
          --selectors namespace=monitoring
        log_info "monitoring-profile 생성 요청됨 (완료까지 5-10분 소요)"
    fi
    
    # kube-system용 (CoreDNS용)
    if aws eks describe-fargate-profile --cluster-name $CLUSTER_NAME --fargate-profile-name kube-system-profile >/dev/null 2>&1; then
        log_success "Fargate Profile 'kube-system-profile' 이미 존재"
    else
        log_info "Fargate Profile 'kube-system-profile' 생성 중..."
        aws eks create-fargate-profile \
          --cluster-name $CLUSTER_NAME \
          --fargate-profile-name kube-system-profile \
          --pod-execution-role-arn arn:aws:iam::${ACCOUNT_ID}:role/EKSFargatePodExecutionRole \
          --subnets $PRIVATE_SUBNET_1 $PRIVATE_SUBNET_2 \
          --selectors namespace=kube-system,labels='{k8s-app=kube-dns}'
        log_info "kube-system-profile 생성 요청됨 (완료까지 5-10분 소요)"
    fi
}

# Step 5: Fargate Profile 상태 확인
check_fargate_status() {
    log_info "Fargate Profile 상태 확인 중..."
    
    profiles=("eatcloud-profile" "monitoring-profile" "kube-system-profile")
    
    for profile in "${profiles[@]}"; do
        status=$(aws eks describe-fargate-profile --cluster-name $CLUSTER_NAME --fargate-profile-name $profile --query "fargateProfile.status" --output text 2>/dev/null || echo "NOT_FOUND")
        
        case $status in
            "ACTIVE")
                log_success "Fargate Profile '$profile': ACTIVE ✅"
                ;;
            "CREATING")
                log_warning "Fargate Profile '$profile': CREATING (대기 중...)"
                ;;
            "CREATE_FAILED")
                log_error "Fargate Profile '$profile': CREATE_FAILED ❌"
                ;;
            "NOT_FOUND")
                log_warning "Fargate Profile '$profile': 아직 생성되지 않음"
                ;;
            *)
                log_info "Fargate Profile '$profile': $status"
                ;;
        esac
    done
}

# Step 6: CoreDNS 패치 (필요시)
patch_coredns() {
    log_info "CoreDNS Fargate 호환성 확인 중..."
    
    # CoreDNS deployment 존재 확인
    if kubectl get deployment coredns -n kube-system >/dev/null 2>&1; then
        log_info "CoreDNS Fargate 호환성 패치 적용 중..."
        kubectl patch deployment coredns \
          -n kube-system \
          --type json \
          -p='[{"op": "remove", "path": "/spec/template/metadata/annotations/eks.amazonaws.com~1compute-type"}]' 2>/dev/null || true
        log_success "CoreDNS 패치 완료"
    else
        log_warning "CoreDNS deployment를 찾을 수 없습니다"
    fi
}

# Step 7: 상태 확인
show_status() {
    log_info "전체 상태 확인 중..."
    
    echo ""
    echo "📊 클러스터 정보:"
    echo "==========================================="
    echo "클러스터: $CLUSTER_NAME"
    echo "리전: $REGION"
    echo "VPC: $VPC_ID"
    
    echo ""
    echo "📦 네임스페이스:"
    echo "==========================================="
    kubectl get namespaces | grep -E "(eatcloud|monitoring|aws-observability)" || echo "생성된 네임스페이스 없음"
    
    echo ""
    echo "🚀 Fargate Profile 목록:"
    echo "==========================================="
    aws eks list-fargate-profiles --cluster-name $CLUSTER_NAME --output table || echo "Fargate Profile 없음"
    
    echo ""
    echo "📋 노드 상태:"
    echo "==========================================="
    kubectl get nodes -o wide || echo "노드 정보 없음"
}

# 메인 실행
main() {
    log_info "EKS Fargate 설정 시작 🚀"
    
    setup_kubeconfig
    create_iam_roles
    create_namespaces
    create_fargate_profiles
    
    echo ""
    log_info "Fargate Profile 생성이 진행 중입니다..."
    log_info "완료까지 5-10분 정도 소요됩니다."
    
    check_fargate_status
    patch_coredns
    show_status
    
    log_success "✅ EKS Fargate 설정이 완료되었습니다!"
    
    echo ""
    echo "💡 다음 단계:"
    echo "  1. Fargate Profile 완료 대기: aws eks describe-fargate-profile --cluster-name $CLUSTER_NAME --fargate-profile-name eatcloud-profile"
    echo "  2. 상태 재확인: ./setup-fargate.sh"
    echo "  3. Kinesis 파이프라인 설정: ./setup-kinesis.sh"
    
    echo ""
    echo "🔍 유용한 명령어:"
    echo "  - Profile 상태 확인: aws eks list-fargate-profiles --cluster-name $CLUSTER_NAME"
    echo "  - 노드 확인: kubectl get nodes"
    echo "  - Pod 확인: kubectl get pods --all-namespaces"
}

# 스크립트 실행
main "$@"
