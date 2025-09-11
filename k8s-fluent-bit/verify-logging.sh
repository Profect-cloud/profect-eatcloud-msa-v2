#!/bin/bash

# 🔍 EatCloud MSA Fluent Bit 로깅 검증 스크립트

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

# MSA 서비스 목록
SERVICES=(
    "auth-service"
    "admin-service"
    "customer-service" 
    "store-service"
    "order-service"
    "payment-service"
    "manager-service"
)

echo -e "${BLUE}🔍 EatCloud MSA Fluent Bit 로깅 검증을 시작합니다...${NC}"

# 헬퍼 함수들
print_section() {
    echo ""
    echo -e "${PURPLE}======================================${NC}"
    echo -e "${PURPLE} $1${NC}"
    echo -e "${PURPLE}======================================${NC}"
}

# 1. 전체 Pod 상태 확인
print_section "1. 전체 Pod 상태 확인"

echo -e "${YELLOW}📊 모든 Pod 상태:${NC}"
kubectl get pods -n ${NAMESPACE} -o wide

echo ""
echo -e "${YELLOW}🔍 서비스별 상세 상태:${NC}"
for service_name in "${SERVICES[@]}"; do
    pod_name=$(kubectl get pod -l app=${service_name} -n ${NAMESPACE} -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    if [ -n "$pod_name" ]; then
        pod_status=$(kubectl get pod ${pod_name} -n ${NAMESPACE} -o jsonpath='{.status.phase}' 2>/dev/null || echo "UNKNOWN")
        
        if [ "$pod_status" = "Running" ]; then
            echo -e "  ${service_name}: ${GREEN}${pod_status}${NC} (Pod: ${pod_name})"
        else
            echo -e "  ${service_name}: ${RED}${pod_status}${NC} (Pod: ${pod_name})"
        fi
    else
        echo -e "  ${service_name}: ${RED}NOT_FOUND${NC}"
    fi
done

read -p "계속 진행하시겠습니까? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    exit 1
fi

# 2. 로그 파일 검증
print_section "2. 서비스별 로그 파일 검증"

for service_name in "${SERVICES[@]}"; do
    pod_name=$(kubectl get pod -l app=${service_name} -n ${NAMESPACE} -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    if [ -n "$pod_name" ]; then
        echo ""
        echo -e "${BLUE}📂 ${service_name} 로그 파일 검증${NC}"
        
        # 로그 디렉토리 확인
        if kubectl exec ${pod_name} -n ${NAMESPACE} -c ${service_name} -- ls /var/log/app/ >/dev/null 2>&1; then
            echo -e "  로그 디렉토리: ${GREEN}✅ 존재${NC}"
            
            # 로그 파일 목록
            echo -e "  📁 로그 파일 목록:"
            kubectl exec ${pod_name} -n ${NAMESPACE} -c ${service_name} -- ls -la /var/log/app/ | sed 's/^/    /'
            
            # 각 로그 타입별 확인
            for log_type in "stateful" "stateless" "recommendation" "error"; do
                log_file="${service_name}-${log_type}.log"
                if kubectl exec ${pod_name} -n ${NAMESPACE} -c ${service_name} -- test -f /var/log/app/${log_file} 2>/dev/null; then
                    size=$(kubectl exec ${pod_name} -n ${NAMESPACE} -c ${service_name} -- wc -l /var/log/app/${log_file} 2>/dev/null | awk '{print $1}')
                    if [ "$size" -gt 0 ]; then
                        echo -e "    ${log_file}: ${GREEN}${size} lines${NC}"
                    else
                        echo -e "    ${log_file}: ${YELLOW}0 lines (빈 파일)${NC}"
                    fi
                else
                    echo -e "    ${log_file}: ${RED}없음${NC}"
                fi
            done
        else
            echo -e "  로그 디렉토리: ${RED}❌ 없음${NC}"
        fi
    else
        echo -e "${RED}❌ ${service_name} Pod를 찾을 수 없습니다.${NC}"
    fi
done

read -p "계속 진행하시겠습니까? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    exit 1
fi

# 3. Fluent Bit 상태 검증
print_section "3. Fluent Bit 상태 검증"

for service_name in "${SERVICES[@]}"; do
    pod_name=$(kubectl get pod -l app=${service_name} -n ${NAMESPACE} -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    if [ -n "$pod_name" ]; then
        echo ""
        echo -e "${BLUE}🔄 ${service_name} Fluent Bit 상태 검증${NC}"
        
        # Fluent Bit 컨테이너 존재 확인
        if kubectl get pod ${pod_name} -n ${NAMESPACE} -o jsonpath='{.spec.containers[*].name}' | grep -q fluent-bit; then
            echo -e "  Fluent Bit 컨테이너: ${GREEN}✅ 존재${NC}"
            
            # 헬스체크
            if kubectl exec ${pod_name} -n ${NAMESPACE} -c fluent-bit -- curl -s http://localhost:2020/api/v1/health >/dev/null 2>&1; then
                echo -e "  헬스체크: ${GREEN}✅ 정상${NC}"
            else
                echo -e "  헬스체크: ${RED}❌ 실패${NC}"
            fi
            
        else
            echo -e "  Fluent Bit 컨테이너: ${RED}❌ 없음${NC}"
        fi
    fi
done

echo ""
echo -e "${GREEN}🎉 EatCloud MSA Fluent Bit 로깅 검증이 완료되었습니다!${NC}"
