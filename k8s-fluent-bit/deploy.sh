#!/bin/bash

# 🚀 EatCloud MSA 로깅 시스템 상세 배포 스크립트
# 이 스크립트는 단계별로 배포를 진행하며 각 단계에서 확인을 요청합니다.

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m'

# 변수 정의
NAMESPACE="dev"
CLUSTER_NAME="eatcloud"
AWS_REGION="ap-northeast-2"

echo -e "${BLUE}🚀 EatCloud MSA 로깅 시스템 상세 배포를 시작합니다...${NC}"

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
for tool in kubectl aws; do
    if command -v $tool &> /dev/null; then
        echo -e "${GREEN}✅ $tool이 설치되어 있습니다.${NC}"
    else
        echo -e "${RED}❌ $tool이 설치되지 않았습니다.${NC}"
        exit 1
    fi
done

ask_continue

# 2. Fluent Bit 설정 배포
print_section "2. Fluent Bit 설정 배포"

echo -e "${YELLOW}📋 Fluent Bit ConfigMap 배포 중...${NC}"
kubectl apply -f 01-fluent-bit-configmap.yaml
echo -e "${GREEN}✅ Fluent Bit ConfigMap 배포 완료${NC}"

echo -e "${YELLOW}🔐 Fluent Bit RBAC 배포 중...${NC}"
kubectl apply -f 02-fluent-bit-rbac.yaml
echo -e "${GREEN}✅ Fluent Bit RBAC 배포 완료${NC}"

ask_continue

# 3. 서비스 배포
print_section "3. 서비스 배포"

echo -e "${YELLOW}🏢 Admin Service 배포 중...${NC}"
kubectl apply -f 03-admin-service-deployment.yaml
echo -e "${GREEN}✅ Admin Service 배포 완료${NC}"

echo -e "${YELLOW}👥 Customer Service 배포 중...${NC}"
kubectl apply -f 04-customer-service-deployment.yaml
echo -e "${GREEN}✅ Customer Service 배포 완료${NC}"

# Pod 시작 대기
echo -e "${YELLOW}⏳ Pod들이 시작될 때까지 대기 중...${NC}"
echo -e "${BLUE}🔄 Admin Service Pod 대기 중...${NC}"
kubectl wait --for=condition=Ready pod -l app=admin-service -n ${NAMESPACE} --timeout=300s || true

echo -e "${BLUE}🔄 Customer Service Pod 대기 중...${NC}"
kubectl wait --for=condition=Ready pod -l app=customer-service -n ${NAMESPACE} --timeout=300s || true

# Pod 상태 확인
echo -e "${YELLOW}📊 Pod 상태 확인:${NC}"
kubectl get pods -n ${NAMESPACE} -o wide

ask_continue

# 6. Ingress 배포
print_section "6. Ingress 배포"

echo -e "${YELLOW}🌐 Internal ALB Ingress 배포 중...${NC}"
kubectl apply -f 05-eatcloud-ingress.yaml
echo -e "${GREEN}✅ Ingress 배포 완료${NC}"

# Ingress 상태 확인
echo -e "${YELLOW}🔍 Ingress 상태 확인 중...${NC}"
kubectl get ingress -n ${NAMESPACE}

ask_continue

# 7. 배포 검증
print_section "7. 배포 검증"

echo -e "${YELLOW}🔍 전체 리소스 상태 확인:${NC}"
kubectl get all -n ${NAMESPACE}

echo ""
echo -e "${YELLOW}📋 Fluent Bit 컨테이너 확인:${NC}"
for pod in $(kubectl get pods -n ${NAMESPACE} -o name); do
    pod_name=$(echo $pod | cut -d'/' -f2)
    echo ""
    echo -e "${BLUE}📦 Pod: ${pod_name}${NC}"
    
    # 컨테이너 목록
    containers=$(kubectl get pod ${pod_name} -n ${NAMESPACE} -o jsonpath='{.spec.containers[*].name}')
    echo -e "  컨테이너: ${containers}"
    
    # Fluent Bit 헬스체크
    if echo "$containers" | grep -q fluent-bit; then
        echo -e "  Fluent Bit 헬스체크:"
        if kubectl exec ${pod_name} -n ${NAMESPACE} -c fluent-bit -- curl -s http://localhost:2020/api/v1/health >/dev/null 2>&1; then
            echo -e "    ${GREEN}✅ 헬스체크 성공${NC}"
        else
            echo -e "    ${RED}❌ 헬스체크 실패${NC}"
        fi
    fi
done

echo ""
echo -e "${YELLOW}📂 로그 파일 확인:${NC}"
for pod in $(kubectl get pods -n ${NAMESPACE} -o name); do
    pod_name=$(echo $pod | cut -d'/' -f2)
    app_name=$(kubectl get pod ${pod_name} -n ${NAMESPACE} -o jsonpath='{.metadata.labels.app}')
    
    echo ""
    echo -e "${BLUE}📂 ${app_name} 로그 파일:${NC}"
    if kubectl exec ${pod_name} -n ${NAMESPACE} -c ${app_name} -- ls -la /var/log/app/ >/dev/null 2>&1; then
        kubectl exec ${pod_name} -n ${NAMESPACE} -c ${app_name} -- ls -la /var/log/app/ | sed 's/^/  /'
    else
        echo -e "  ${RED}❌ 로그 디렉토리에 접근할 수 없습니다${NC}"
    fi
done

# 8. 배포 완료 및 다음 단계
print_section "8. 배포 완료"

echo -e "${GREEN}🎉 EatCloud MSA 로깅 시스템 배포가 완료되었습니다!${NC}"
echo ""

echo -e "${BLUE}📋 배포된 리소스 요약:${NC}"
echo -e "  • ConfigMap: fluent-bit-config"
echo -e "  • ServiceAccount: fluent-bit-service-account"
echo -e "  • Deployment: admin-service, customer-service"
echo -e "  • Service: admin-service, customer-service"
echo -e "  • Ingress: eatcloud-ingress"

echo ""
echo -e "${BLUE}🔗 접속 정보:${NC}"
alb_address=$(kubectl get ingress eatcloud-ingress -n ${NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "대기 중...")
echo -e "  • ALB 주소: ${alb_address}"
echo -e "  • Admin Service: http://${alb_address}/admin"
echo -e "  • Customer Service: http://${alb_address}/customer"

echo ""
echo -e "${BLUE}📊 모니터링:${NC}"
echo -e "  • Pod 상태: kubectl get pods -n ${NAMESPACE}"
echo -e "  • 로그 확인: kubectl logs -n ${NAMESPACE} <pod-name> -c fluent-bit"
echo -e "  • Fluent Bit 메트릭: kubectl port-forward -n ${NAMESPACE} <pod-name> 2020:2020"

echo ""
echo -e "${BLUE}🔧 문제 해결:${NC}"
echo -e "  • 자동 진단: ./troubleshoot.sh"
echo -e "  • 수동 확인: kubectl describe pod <pod-name> -n ${NAMESPACE}"

echo ""
echo -e "${BLUE}📝 다음 단계:${NC}"
echo -e "  1. 애플리케이션 API 테스트"
echo -e "  2. 로그 생성 확인"
echo -e "  3. Kinesis 데이터 스트림 확인"
echo -e "  4. 추천 이벤트 활성화 테스트"

echo ""
echo -e "${GREEN}✨ 배포가 성공적으로 완료되었습니다!${NC}"료${NC}"

echo -e "${YELLOW}👥 Customer Service 배포 중...${NC}"
kubectl apply -f 04-customer-service-deployment.yaml
echo -e "${GREEN}✅ Customer Service 배포 완료${NC}"

echo -e "${YELLOW}⏳ Pod들이 시작될 때까지 대기 중...${NC}"
kubectl wait --for=condition=Ready pod -l app=admin-service -n ${NAMESPACE} --timeout=300s || true
kubectl wait --for=condition=Ready pod -l app=customer-service -n ${NAMESPACE} --timeout=300s || true

ask_continue

# 4. Ingress 배포
print_section "4. Ingress 배포"

echo -e "${YELLOW}🌐 Internal ALB Ingress 배포 중...${NC}"
kubectl apply -f 05-eatcloud-ingress.yaml
echo -e "${GREEN}✅ Ingress 배포 완료${NC}"

ask_continue

# 5. 배포 완료
print_section "5. 배포 완료"

echo -e "${GREEN}🎉 EatCloud MSA 로깅 시스템 배포가 완료되었습니다!${NC}"
echo ""
echo -e "${BLUE}📋 배포된 리소스:${NC}"
kubectl get all -n ${NAMESPACE}

echo ""
echo -e "${BLUE}🔧 문제 해결:${NC}"
echo -e "  • 자동 진단: ./troubleshoot.sh"
echo -e "  • Pod 상태: kubectl get pods -n ${NAMESPACE}"

echo ""
echo -e "${GREEN}✨ 배포가 성공적으로 완료되었습니다!${NC}"
