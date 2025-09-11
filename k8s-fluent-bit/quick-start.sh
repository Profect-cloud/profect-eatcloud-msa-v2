#!/bin/bash

# 🚀 EatCloud MSA 로깅 시스템 빠른 배포 스크립트
# 
# 이 스크립트는 다음을 자동으로 배포합니다:
# 1. Fluent Bit ConfigMap 및 RBAC
# 2. Admin Service + Fluent Bit 사이드카
# 3. Customer Service + Fluent Bit 사이드카
# 4. Internal ALB Ingress

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 변수 정의
NAMESPACE="dev"
CLUSTER_NAME="eatcloud"
AWS_REGION="ap-northeast-2"

echo -e "${BLUE}🚀 EatCloud MSA 로깅 시스템 빠른 배포를 시작합니다...${NC}"

# 1. Prerequisites 확인
echo -e "${YELLOW}🔍 Prerequisites 확인 중...${NC}"

# kubectl 확인
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}❌ kubectl이 설치되지 않았습니다.${NC}"
    exit 1
fi

# AWS CLI 확인
if ! command -v aws &> /dev/null; then
    echo -e "${RED}❌ AWS CLI가 설치되지 않았습니다.${NC}"
    exit 1
fi

# EKS 컨텍스트 확인
current_context=$(kubectl config current-context 2>/dev/null || echo "none")
if [[ $current_context != *"eatcloud"* ]]; then
    echo -e "${YELLOW}⚠️  EKS 컨텍스트를 설정합니다...${NC}"
    aws eks update-kubeconfig --region ${AWS_REGION} --name ${CLUSTER_NAME}
fi

# Namespace 확인
if ! kubectl get namespace ${NAMESPACE} >/dev/null 2>&1; then
    echo -e "${GREEN}✅ Namespace ${NAMESPACE}를 생성합니다...${NC}"
    kubectl create namespace ${NAMESPACE}
else
    echo -e "${GREEN}✅ Namespace ${NAMESPACE}가 이미 존재합니다.${NC}"
fi

# 2. Fluent Bit ConfigMap 배포
echo -e "${YELLOW}📋 Fluent Bit ConfigMap 배포 중...${NC}"
kubectl apply -f 01-fluent-bit-configmap.yaml

# 3. Fluent Bit RBAC 배포
echo -e "${YELLOW}🔐 Fluent Bit RBAC 배포 중...${NC}"
kubectl apply -f 02-fluent-bit-rbac.yaml

# 4. 이미지 빌드 및 푸시 확인
echo -e "${YELLOW}🐳 ECR 이미지 확인 중...${NC}"
check_image() {
    local service_name=$1
    local image_uri="536580887516.dkr.ecr.ap-northeast-2.amazonaws.com/eatcloud/${service_name}:latest"
    
    if aws ecr describe-images --region ${AWS_REGION} --repository-name "eatcloud/${service_name}" --image-ids imageTag=latest >/dev/null 2>&1; then
        echo -e "${GREEN}✅ ${service_name} 이미지가 ECR에 존재합니다.${NC}"
        return 0
    else
        echo -e "${RED}❌ ${service_name} 이미지가 ECR에 없습니다.${NC}"
        echo -e "${YELLOW}💡 이미지를 빌드하시겠습니까? (y/N)${NC}"
        read -r response
        if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
            build_and_push_image $service_name
        else
            echo -e "${RED}❌ 이미지 없이는 배포를 계속할 수 없습니다.${NC}"
            return 1
        fi
    fi
}

build_and_push_image() {
    local service_name=$1
    echo -e "${BLUE}🔨 ${service_name} 이미지 빌드 및 푸시 중...${NC}"
    
    # 서비스 디렉토리로 이동
    cd ../${service_name}
    
    # Gradle 빌드
    echo -e "${YELLOW}📦 Gradle 빌드 중...${NC}"
    ./gradlew clean bootJar
    
    # ECR 로그인
    aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com
    
    # Docker 빌드 및 푸시
    docker build -t eatcloud/${service_name} .
    docker tag eatcloud/${service_name}:latest 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com/eatcloud/${service_name}:latest
    docker push 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com/eatcloud/${service_name}:latest
    
    # 원래 디렉토리로 복귀
    cd ../k8s-fluent-bit
    
    echo -e "${GREEN}✅ ${service_name} 이미지 빌드 및 푸시 완료${NC}"
}

# 이미지 확인
check_image "admin-service"
check_image "customer-service"

# 5. Admin Service 배포
echo -e "${YELLOW}🏢 Admin Service 배포 중...${NC}"
kubectl apply -f 03-admin-service-deployment.yaml

# 6. Customer Service 배포
echo -e "${YELLOW}👥 Customer Service 배포 중...${NC}"
kubectl apply -f 04-customer-service-deployment.yaml

# 7. Ingress 배포
echo -e "${YELLOW}🌐 Internal ALB Ingress 배포 중...${NC}"
kubectl apply -f 05-eatcloud-ingress.yaml

# 8. 배포 상태 확인
echo -e "${YELLOW}⏳ 배포 상태 확인 중...${NC}"

# Pod 시작 대기
echo -e "${BLUE}🔄 Pod들이 시작될 때까지 대기 중...${NC}"
kubectl wait --for=condition=Ready pod -l app=admin-service -n ${NAMESPACE} --timeout=300s
kubectl wait --for=condition=Ready pod -l app=customer-service -n ${NAMESPACE} --timeout=300s

# 9. 배포 결과 출력
echo -e "${GREEN}🎉 배포가 완료되었습니다!${NC}"
echo ""

echo -e "${BLUE}📋 배포된 리소스:${NC}"
kubectl get pods,svc,ingress -n ${NAMESPACE}

echo ""
echo -e "${BLUE}🔍 상태 확인 명령어:${NC}"
echo -e "  • Pod 상태: kubectl get pods -n ${NAMESPACE}"
echo -e "  • 로그 확인: kubectl logs -n ${NAMESPACE} <pod-name> -c fluent-bit"
echo -e "  • 서비스 확인: kubectl get svc -n ${NAMESPACE}"
echo -e "  • Ingress 확인: kubectl get ingress -n ${NAMESPACE}"

echo ""
echo -e "${BLUE}📊 Fluent Bit 메트릭:${NC}"
echo -e "  • Admin Service: kubectl port-forward -n ${NAMESPACE} svc/admin-service 2020:2020"
echo -e "  • Customer Service: kubectl port-forward -n ${NAMESPACE} svc/customer-service 2021:2020"
echo -e "  • 메트릭 URL: http://localhost:2020/api/v1/health"

echo ""
echo -e "${BLUE}🐛 문제 해결:${NC}"
echo -e "  • 자동 진단: ./troubleshoot.sh"
echo -e "  • 로그 파일 확인: kubectl exec -n ${NAMESPACE} <pod-name> -c admin-service -- ls -la /var/log/app/"

echo ""
echo -e "${BLUE}🔄 Kinesis Stream 상태:${NC}"
for stream in "eatcloud-stateful-logs" "eatcloud-stateless-logs" "eatcloud-recommendation-events"; do
    status=$(aws kinesis describe-stream --stream-name ${stream} --region ${AWS_REGION} --query 'StreamDescription.StreamStatus' --output text 2>/dev/null || echo "NOT_FOUND")
    if [ "$status" = "ACTIVE" ]; then
        echo -e "  • ${stream}: ${GREEN}${status}${NC}"
    else
        echo -e "  • ${stream}: ${RED}${status}${NC}"
    fi
done

echo ""
echo -e "${YELLOW}📝 다음 단계:${NC}"
echo -e "  1. 애플리케이션 테스트를 통한 로그 생성"
echo -e "  2. Kinesis 데이터 확인"
echo -e "  3. 추천 이벤트 활성화 (application.properties에 logging.recommendation.enabled=true)"
echo ""
echo -e "${GREEN}✨ EatCloud MSA 로깅 시스템이 성공적으로 배포되었습니다!${NC}"
