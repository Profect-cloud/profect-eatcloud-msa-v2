#!/bin/bash

# EKS 액세스 권한 설정 및 Fargate Profile 생성
# kubectl 없이 AWS CLI만으로 진행

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

# 환경변수 설정
export CLUSTER_NAME="eatcloud"
export ACCOUNT_ID="536580887516"
export REGION="ap-northeast-2"
export VPC_ID="vpc-0bdbee988c0d5e2cc"
export PRIVATE_SUBNET_1="subnet-029b4e47d0be0c4b5"
export PRIVATE_SUBNET_2="subnet-0c66ca1fea24116a5"

log_info "🚀 EKS 액세스 권한 설정 시작"
log_info "클러스터: ${CLUSTER_NAME}"
log_info "사용자: eatcloud"

# Step 1: 현재 상태 확인
check_current_status() {
    log_info "현재 상태 확인 중..."
    
    echo "🔍 현재 IAM 사용자:"
    aws sts get-caller-identity
    
    echo ""
    echo "🔍 EKS 클러스터 상태:"
    aws eks describe-cluster --name $CLUSTER_NAME --region $REGION --query 'cluster.status'
    
    echo ""
    echo "🔍 기존 Fargate Profile:"
    aws eks list-fargate-profiles --cluster-name $CLUSTER_NAME --region $REGION 2>/dev/null || echo "기존 Profile 없음"
}

# Step 2: Fargate Profile 생성 (kubectl 없이)
create_fargate_profiles() {
    log_info "Fargate Profile 생성 중..."
    
    # eatcloud namespace용
    if aws eks describe-fargate-profile --cluster-name $CLUSTER_NAME --fargate-profile-name eatcloud-profile --region $REGION >/dev/null 2>&1; then
        log_success "Fargate Profile 'eatcloud-profile' 이미 존재"
    else
        log_info "Fargate Profile 'eatcloud-profile' 생성 중..."
        aws eks create-fargate-profile \
          --cluster-name $CLUSTER_NAME \
          --fargate-profile-name eatcloud-profile \
          --pod-execution-role-arn arn:aws:iam::${ACCOUNT_ID}:role/EKSFargatePodExecutionRole \
          --subnets $PRIVATE_SUBNET_1 $PRIVATE_SUBNET_2 \
          --selectors namespace=eatcloud \
          --region $REGION
        
        if [ $? -eq 0 ]; then
            log_success "eatcloud-profile 생성 요청 완료"
        else
            log_error "eatcloud-profile 생성 실패"
        fi
    fi
    
    # monitoring namespace용
    if aws eks describe-fargate-profile --cluster-name $CLUSTER_NAME --fargate-profile-name monitoring-profile --region $REGION >/dev/null 2>&1; then
        log_success "Fargate Profile 'monitoring-profile' 이미 존재"
    else
        log_info "Fargate Profile 'monitoring-profile' 생성 중..."
        aws eks create-fargate-profile \
          --cluster-name $CLUSTER_NAME \
          --fargate-profile-name monitoring-profile \
          --pod-execution-role-arn arn:aws:iam::${ACCOUNT_ID}:role/EKSFargatePodExecutionRole \
          --subnets $PRIVATE_SUBNET_1 $PRIVATE_SUBNET_2 \
          --selectors namespace=monitoring \
          --region $REGION
        
        if [ $? -eq 0 ]; then
            log_success "monitoring-profile 생성 요청 완료"
        else
            log_error "monitoring-profile 생성 실패"
        fi
    fi
    
    # kube-system용 (CoreDNS)
    if aws eks describe-fargate-profile --cluster-name $CLUSTER_NAME --fargate-profile-name kube-system-profile --region $REGION >/dev/null 2>&1; then
        log_success "Fargate Profile 'kube-system-profile' 이미 존재"
    else
        log_info "Fargate Profile 'kube-system-profile' 생성 중..."
        aws eks create-fargate-profile \
          --cluster-name $CLUSTER_NAME \
          --fargate-profile-name kube-system-profile \
          --pod-execution-role-arn arn:aws:iam::${ACCOUNT_ID}:role/EKSFargatePodExecutionRole \
          --subnets $PRIVATE_SUBNET_1 $PRIVATE_SUBNET_2 \
          --selectors namespace=kube-system,labels='{k8s-app=kube-dns}' \
          --region $REGION
        
        if [ $? -eq 0 ]; then
            log_success "kube-system-profile 생성 요청 완료"
        else
            log_error "kube-system-profile 생성 실패"
        fi
    fi
}

# Step 3: Fargate Profile 상태 확인
check_fargate_status() {
    log_info "Fargate Profile 상태 확인 중..."
    
    profiles=("eatcloud-profile" "monitoring-profile" "kube-system-profile")
    
    echo ""
    echo "📋 Fargate Profile 상태:"
    echo "==========================================="
    
    for profile in "${profiles[@]}"; do
        status=$(aws eks describe-fargate-profile --cluster-name $CLUSTER_NAME --fargate-profile-name $profile --region $REGION --query "fargateProfile.status" --output text 2>/dev/null || echo "NOT_FOUND")
        
        case $status in
            "ACTIVE")
                echo "✅ $profile: ACTIVE"
                ;;
            "CREATING")
                echo "🟡 $profile: CREATING (대기 중...)"
                ;;
            "CREATE_FAILED")
                echo "❌ $profile: CREATE_FAILED"
                # 실패 원인 확인
                aws eks describe-fargate-profile --cluster-name $CLUSTER_NAME --fargate-profile-name $profile --region $REGION --query "fargateProfile.status" 2>/dev/null || true
                ;;
            "NOT_FOUND")
                echo "⚪ $profile: 아직 생성되지 않음"
                ;;
            *)
                echo "🔍 $profile: $status"
                ;;
        esac
    done
    
    echo ""
    echo "📊 전체 Fargate Profile 목록:"
    echo "==========================================="
    aws eks list-fargate-profiles --cluster-name $CLUSTER_NAME --region $REGION --output table 2>/dev/null || echo "Profile 목록을 가져올 수 없습니다"
}

# Step 4: kubectl 접근 문제 해결 가이드
provide_kubectl_fix_guide() {
    log_info "kubectl 접근 문제 해결 가이드"
    
    echo ""
    echo "🔧 kubectl 인증 문제 해결방법:"
    echo "==========================================="
    echo "1. EKS 클러스터에 현재 IAM 사용자 'eatcloud' 권한 추가 필요"
    echo ""
    echo "2. AWS 콘솔에서 해결:"
    echo "   - EKS 콘솔 → 클러스터 '$CLUSTER_NAME' → 액세스 탭"
    echo "   - 'IAM 액세스 항목 생성' 클릭"
    echo "   - IAM 주체: arn:aws:iam::${ACCOUNT_ID}:user/eatcloud"
    echo "   - 정책: AmazonEKSClusterAdminPolicy"
    echo ""
    echo "3. 또는 클러스터 생성 시 사용한 IAM 사용자/역할로 작업"
    echo ""
    echo "4. 임시 해결책 - AWS CLI로만 작업:"
    echo "   - Fargate Profile: AWS CLI ✅"
    echo "   - 애플리케이션 배포: AWS CLI + YAML 파일"
    echo "   - 모니터링: AWS CloudWatch + CLI"
}

# Step 5: 다음 단계 안내
show_next_steps() {
    log_info "다음 단계 안내"
    
    echo ""
    echo "💡 현재 완료된 작업:"
    echo "  ✅ IAM 역할 생성 (EKSFargatePodExecutionRole)"
    echo "  ✅ Fargate Profile 생성 요청"
    echo ""
    echo "🔄 진행 중인 작업:"
    echo "  🟡 Fargate Profile 활성화 (5-10분 소요)"
    echo ""
    echo "⏭️ 다음 단계:"
    echo "  1. Fargate Profile 완료 대기:"
    echo "     ./setup-eks-access.sh  # 상태 재확인"
    echo ""
    echo "  2. kubectl 권한 문제 해결 후:"
    echo "     kubectl create namespace eatcloud"
    echo "     kubectl create namespace monitoring"
    echo ""
    echo "  3. 애플리케이션 배포:"
    echo "     ./deploy-eatcloud-fargate.sh"
    echo ""
    echo "🔍 상태 확인 명령어:"
    echo "  - Profile 상태: aws eks describe-fargate-profile --cluster-name $CLUSTER_NAME --fargate-profile-name eatcloud-profile --region $REGION"
    echo "  - 전체 Profile: aws eks list-fargate-profiles --cluster-name $CLUSTER_NAME --region $REGION"
}

# 메인 실행
main() {
    check_current_status
    echo ""
    create_fargate_profiles
    echo ""
    check_fargate_status
    echo ""
    provide_kubectl_fix_guide
    echo ""
    show_next_steps
    
    log_success "🎉 EKS Fargate Profile 설정이 시작되었습니다!"
    log_info "5-10분 후 상태를 다시 확인해주세요."
}

# 스크립트 실행
main "$@"
