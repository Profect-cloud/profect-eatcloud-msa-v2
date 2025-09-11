#!/bin/bash

# EKS 클러스터 "eatcloud" 설정 스크립트
# Phase 2: Fargate 설정부터 시작

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

# 환경변수 설정 (EKS 클러스터 정보 기반)
export CLUSTER_NAME="eatcloud"
export ACCOUNT_ID="536580887516"
export REGION="ap-northeast-2"
export VPC_ID="vpc-0bdbee988c0d5e2cc"
export PRIVATE_SUBNET_1="subnet-029b4e47d0be0c4b5"
export PRIVATE_SUBNET_2="subnet-0c66ca1fea24116a5"

log_info "🚀 EKS 클러스터 '${CLUSTER_NAME}' 설정을 시작합니다..."
log_info "계정 ID: ${ACCOUNT_ID}"
log_info "리전: ${REGION}"
log_info "VPC ID: ${VPC_ID}"

# Step 1: 필수 도구 설치 확인
check_tools() {
    log_info "필수 도구 설치 확인 중..."
    
    # kubectl 확인
    if ! command -v kubectl &> /dev/null; then
        log_info "kubectl 설치 중..."
        curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
        sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
        rm kubectl
        log_success "kubectl 설치 완료"
    else
        log_success "kubectl 이미 설치됨"
    fi
    
    # eksctl 확인
    if ! command -v eksctl &> /dev/null; then
        log_info "eksctl 설치 중..."
        curl --silent --location "https://github.com/weaveworks/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp
        sudo mv /tmp/eksctl /usr/local/bin
        log_success "eksctl 설치 완료"
    else
        log_success "eksctl 이미 설치됨"
    fi
    
    # helm 확인
    if ! command -v helm &> /dev/null; then
        log_info "helm 설치 중..."
        curl https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3 | bash
        log_success "helm 설치 완료"
    else
        log_success "helm 이미 설치됨"
    fi
    
    # kubeconfig 업데이트
    log_info "kubeconfig 업데이트 중..."
    aws eks update-kubeconfig --region $REGION --name $CLUSTER_NAME
    log_success "kubeconfig 업데이트 완료"
}

# Step 2: IAM 역할 생성
create_iam_roles() {
    log_info "IAM 역할 생성 중..."
    
    # Fargate Pod Execution Role 체크
    if aws iam get-role --role-name EKSFargatePodExecutionRole >/dev/null 2>&1; then
        log_success "EKSFargatePodExecutionRole 이미 존재"
    else
        log_info "EKSFargatePodExecutionRole 생성 중..."
        
        cat > fargate-trust-policy.json <<EOF
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
          --assume-role-policy-document file://fargate-trust-policy.json
        
        aws iam attach-role-policy \
          --role-name EKSFargatePodExecutionRole \
          --policy-arn arn:aws:iam::aws:policy/AmazonEKSFargatePodExecutionRolePolicy
        
        rm fargate-trust-policy.json
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
    
    profiles=("eatcloud" "monitoring")
    
    for profile in "${profiles[@]}"; do
        if aws eks describe-fargate-profile --cluster-name $CLUSTER_NAME --fargate-profile-name ${profile}-profile >/dev/null 2>&1; then
            log_success "Fargate Profile '${profile}-profile' 이미 존재"
        else
            log_info "Fargate Profile '${profile}-profile' 생성 중..."
            aws eks create-fargate-profile \
              --cluster-name $CLUSTER_NAME \
              --fargate-profile-name ${profile}-profile \
              --pod-execution-role-arn arn:aws:iam::${ACCOUNT_ID}:role/EKSFargatePodExecutionRole \
              --subnets $PRIVATE_SUBNET_1 $PRIVATE_SUBNET_2 \
              --selectors namespace=$profile
            
            log_info "Fargate Profile '${profile}-profile' 생성 중... (몇 분 소요)"
        fi
    done
    
    # kube-system용 Fargate Profile (CoreDNS)
    if aws eks describe-fargate-profile --cluster-name $CLUSTER_NAME --fargate-profile-name kube-system-profile >/dev/null 2>&1; then
        log_success "kube-system Fargate Profile 이미 존재"
    else
        log_info "kube-system Fargate Profile 생성 중..."
        aws eks create-fargate-profile \
          --cluster-name $CLUSTER_NAME \
          --fargate-profile-name kube-system-profile \
          --pod-execution-role-arn arn:aws:iam::${ACCOUNT_ID}:role/EKSFargatePodExecutionRole \
          --subnets $PRIVATE_SUBNET_1 $PRIVATE_SUBNET_2 \
          --selectors namespace=kube-system,labels='{k8s-app=kube-dns}'
        
        log_info "kube-system Fargate Profile 생성 중... (몇 분 소요)"
    fi
}

# Step 5: Fargate Profile 준비 대기
wait_for_fargate_profiles() {
    log_info "Fargate Profile 준비 대기 중..."
    
    profiles=("eatcloud-profile" "monitoring-profile" "kube-system-profile")
    
    for profile in "${profiles[@]}"; do
        log_info "Fargate Profile '$profile' 준비 대기 중..."
        while true; do
            status=$(aws eks describe-fargate-profile --cluster-name $CLUSTER_NAME --fargate-profile-name $profile --query "fargateProfile.status" --output text 2>/dev/null || echo "CREATING")
            if [ "$status" = "ACTIVE" ]; then
                log_success "Fargate Profile '$profile' 활성화됨"
                break
            elif [ "$status" = "CREATE_FAILED" ]; then
                log_error "Fargate Profile '$profile' 생성 실패"
                exit 1
            else
                log_info "Fargate Profile '$profile' 상태: $status (대기 중...)"
                sleep 30
            fi
        done
    done
}

# Step 6: CoreDNS 패치
patch_coredns() {
    log_info "CoreDNS Fargate 호환성 패치 적용 중..."
    
    kubectl patch deployment coredns \
      -n kube-system \
      --type json \
      -p='[{"op": "remove", "path": "/spec/template/metadata/annotations/eks.amazonaws.com~1compute-type"}]' || true
    
    log_success "CoreDNS 패치 완료"
}

# Step 7: 상태 확인
check_status() {
    log_info "클러스터 상태 확인 중..."
    
    echo ""
    echo "📊 노드 상태:"
    echo "===========================================" 
    kubectl get nodes
    
    echo ""
    echo "📦 네임스페이스:"
    echo "===========================================" 
    kubectl get namespaces
    
    echo ""
    echo "🚀 CoreDNS 상태:"
    echo "===========================================" 
    kubectl get pods -n kube-system
    
    echo ""
    echo "📋 Fargate Profile 상태:"
    echo "==========================================="
    aws eks list-fargate-profiles --cluster-name $CLUSTER_NAME --output table
}

# 메인 실행
main() {
    log_info "Phase 2: EKS Fargate 설정 시작"
    
    check_tools
    create_iam_roles
    create_namespaces
    create_fargate_profiles
    wait_for_fargate_profiles
    patch_coredns
    check_status
    
    log_success "🎉 EKS Fargate 설정이 완료되었습니다!"
    
    echo ""
    echo "💡 다음 단계:"
    echo "  1. Kinesis 파이프라인 구축: ./setup-kinesis.sh"
    echo "  2. 모니터링 스택 설치: ./setup-monitoring.sh"
    echo "  3. 애플리케이션 배포: ./deploy-apps.sh"
    
    echo ""
    echo "🔍 유용한 명령어:"
    echo "  - 노드 확인: kubectl get nodes"
    echo "  - Fargate Profile 확인: aws eks list-fargate-profiles --cluster-name $CLUSTER_NAME"
    echo "  - Pod 상태 확인: kubectl get pods --all-namespaces"
}

# 스크립트 실행
main "$@"
