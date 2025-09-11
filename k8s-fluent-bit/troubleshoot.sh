#!/bin/bash

# 🔧 EatCloud MSA 로깅 시스템 문제 해결 도구
# 
# 이 스크립트는 다음을 자동으로 진단합니다:
# 1. Pod 상태 및 로그
# 2. Fluent Bit 설정 및 연결
# 3. Kinesis Stream 상태
# 4. 로그 파일 생성 확인
# 5. IAM 권한 확인

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

echo -e "${BLUE}🔧 EatCloud MSA 로깅 시스템 문제 해결을 시작합니다...${NC}"

# 헬퍼 함수들
print_section() {
    echo ""
    echo -e "${PURPLE}======================================${NC}"
    echo -e "${PURPLE} $1${NC}"
    echo -e "${PURPLE}======================================${NC}"
}

print_subsection() {
    echo ""
    echo -e "${BLUE}--- $1 ---${NC}"
}

check_status() {
    local status=$1
    local message=$2
    
    if [ "$status" = "0" ]; then
        echo -e "${GREEN}✅ $message${NC}"
    else
        echo -e "${RED}❌ $message${NC}"
    fi
}

# 1. 기본 환경 확인
print_section "1. 기본 환경 확인"

print_subsection "kubectl 연결 상태"
if kubectl cluster-info >/dev/null 2>&1; then
    echo -e "${GREEN}✅ kubectl이 클러스터에 연결되어 있습니다.${NC}"
    kubectl cluster-info | head -2
else
    echo -e "${RED}❌ kubectl이 클러스터에 연결되지 않았습니다.${NC}"
    echo -e "${YELLOW}💡 해결방법: aws eks update-kubeconfig --region ${AWS_REGION} --name eatcloud${NC}"
fi

print_subsection "네임스페이스 확인"
if kubectl get namespace ${NAMESPACE} >/dev/null 2>&1; then
    echo -e "${GREEN}✅ Namespace ${NAMESPACE}가 존재합니다.${NC}"
else
    echo -e "${RED}❌ Namespace ${NAMESPACE}가 존재하지 않습니다.${NC}"
    echo -e "${YELLOW}💡 해결방법: kubectl create namespace ${NAMESPACE}${NC}"
fi

# 2. Pod 상태 확인
print_section "2. Pod 상태 확인"

print_subsection "전체 Pod 상태"
kubectl get pods -n ${NAMESPACE} -o wide

print_subsection "Pod 상세 정보"
for pod in $(kubectl get pods -n ${NAMESPACE} -o name 2>/dev/null); do
    pod_name=$(echo $pod | cut -d'/' -f2)
    echo ""
    echo -e "${BLUE}📦 Pod: ${pod_name}${NC}"
    
    # Pod 상태
    status=$(kubectl get pod ${pod_name} -n ${NAMESPACE} -o jsonpath='{.status.phase}' 2>/dev/null || echo "NOT_FOUND")
    if [ "$status" = "Running" ]; then
        echo -e "  상태: ${GREEN}${status}${NC}"
    else
        echo -e "  상태: ${RED}${status}${NC}"
    fi
    
    # 컨테이너 상태
    echo -e "  컨테이너:"
    kubectl get pod ${pod_name} -n ${NAMESPACE} -o jsonpath='{.status.containerStatuses[*].name}' 2>/dev/null | tr ' ' '\n' | while read container; do
        if [ -n "$container" ]; then
            ready=$(kubectl get pod ${pod_name} -n ${NAMESPACE} -o jsonpath="{.status.containerStatuses[?(@.name=='$container')].ready}" 2>/dev/null)
            if [ "$ready" = "true" ]; then
                echo -e "    - ${container}: ${GREEN}Ready${NC}"
            else
                echo -e "    - ${container}: ${RED}Not Ready${NC}"
            fi
        fi
    done
    
    # 최근 이벤트
    echo -e "  최근 이벤트:"
    kubectl describe pod ${pod_name} -n ${NAMESPACE} | grep -A 10 "Events:" | tail -5 | sed 's/^/    /'
done

# 3. Fluent Bit 진단
print_section "3. Fluent Bit 진단"

print_subsection "Fluent Bit ConfigMap"
if kubectl get configmap fluent-bit-config -n ${NAMESPACE} >/dev/null 2>&1; then
    echo -e "${GREEN}✅ Fluent Bit ConfigMap이 존재합니다.${NC}"
else
    echo -e "${RED}❌ Fluent Bit ConfigMap이 존재하지 않습니다.${NC}"
    echo -e "${YELLOW}💡 해결방법: kubectl apply -f 01-fluent-bit-configmap.yaml${NC}"
fi

print_subsection "Service Account & RBAC"
if kubectl get serviceaccount fluent-bit-service-account -n ${NAMESPACE} >/dev/null 2>&1; then
    echo -e "${GREEN}✅ Fluent Bit Service Account가 존재합니다.${NC}"
else
    echo -e "${RED}❌ Fluent Bit Service Account가 존재하지 않습니다.${NC}"
    echo -e "${YELLOW}💡 해결방법: kubectl apply -f 02-fluent-bit-rbac.yaml${NC}"
fi

print_subsection "Fluent Bit 헬스체크"
for pod in $(kubectl get pods -n ${NAMESPACE} -l app=admin-service -o name 2>/dev/null) $(kubectl get pods -n ${NAMESPACE} -l app=customer-service -o name 2>/dev/null); do
    pod_name=$(echo $pod | cut -d'/' -f2)
    echo ""
    echo -e "${BLUE}🔍 Pod: ${pod_name}${NC}"
    
    # Fluent Bit 컨테이너 존재 확인
    if kubectl get pod ${pod_name} -n ${NAMESPACE} -o jsonpath='{.spec.containers[*].name}' | grep -q fluent-bit; then
        echo -e "  Fluent Bit 컨테이너: ${GREEN}존재${NC}"
        
        # Fluent Bit 헬스체크
        echo -e "  헬스체크 시도 중..."
        if kubectl exec ${pod_name} -n ${NAMESPACE} -c fluent-bit -- curl -s http://localhost:2020/api/v1/health >/dev/null 2>&1; then
            echo -e "  헬스체크: ${GREEN}성공${NC}"
        else
            echo -e "  헬스체크: ${RED}실패${NC}"
        fi
        
        # Fluent Bit 메트릭
        echo -e "  메트릭 확인:"
        kubectl exec ${pod_name} -n ${NAMESPACE} -c fluent-bit -- curl -s http://localhost:2020/api/v1/metrics 2>/dev/null | head -10 | sed 's/^/    /'
    else
        echo -e "  Fluent Bit 컨테이너: ${RED}없음${NC}"
    fi
done

# 4. 로그 파일 확인
print_section "4. 로그 파일 확인"

for pod in $(kubectl get pods -n ${NAMESPACE} -l app=admin-service -o name 2>/dev/null) $(kubectl get pods -n ${NAMESPACE} -l app=customer-service -o name 2>/dev/null); do
    pod_name=$(echo $pod | cut -d'/' -f2)
    app_name=$(kubectl get pod ${pod_name} -n ${NAMESPACE} -o jsonpath='{.metadata.labels.app}')
    
    echo ""
    echo -e "${BLUE}📂 ${app_name} 로그 파일 확인${NC}"
    
    # 애플리케이션 컨테이너에서 로그 파일 확인
    if kubectl exec ${pod_name} -n ${NAMESPACE} -c ${app_name} -- ls /var/log/app/ >/dev/null 2>&1; then
        echo -e "  로그 디렉토리: ${GREEN}존재${NC}"
        echo -e "  로그 파일 목록:"
        kubectl exec ${pod_name} -n ${NAMESPACE} -c ${app_name} -- ls -la /var/log/app/ | sed 's/^/    /'
        
        # 각 로그 파일 크기 확인
        for log_type in stateful stateless recommendation; do
            log_file="${app_name}-${log_type}.log"
            if kubectl exec ${pod_name} -n ${NAMESPACE} -c ${app_name} -- test -f /var/log/app/${log_file} 2>/dev/null; then
                size=$(kubectl exec ${pod_name} -n ${NAMESPACE} -c ${app_name} -- wc -l /var/log/app/${log_file} 2>/dev/null | awk '{print $1}')
                if [ "$size" -gt 0 ]; then
                    echo -e "  ${log_file}: ${GREEN}${size} lines${NC}"
                else
                    echo -e "  ${log_file}: ${YELLOW}0 lines (빈 파일)${NC}"
                fi
            else
                echo -e "  ${log_file}: ${RED}없음${NC}"
            fi
        done
    else
        echo -e "  로그 디렉토리: ${RED}없음${NC}"
    fi
done

# 5. Kinesis Stream 상태 확인
print_section "5. Kinesis Stream 상태 확인"

streams=("eatcloud-stateful-logs" "eatcloud-stateless-logs" "eatcloud-recommendation-events")

for stream in "${streams[@]}"; do
    echo ""
    echo -e "${BLUE}🌊 Stream: ${stream}${NC}"
    
    if aws kinesis describe-stream --stream-name ${stream} --region ${AWS_REGION} >/dev/null 2>&1; then
        status=$(aws kinesis describe-stream --stream-name ${stream} --region ${AWS_REGION} --query 'StreamDescription.StreamStatus' --output text)
        shard_count=$(aws kinesis describe-stream --stream-name ${stream} --region ${AWS_REGION} --query 'StreamDescription.Shards | length(@)' --output text)
        
        if [ "$status" = "ACTIVE" ]; then
            echo -e "  상태: ${GREEN}${status}${NC}"
        else
            echo -e "  상태: ${RED}${status}${NC}"
        fi
        echo -e "  Shard 수: ${shard_count}"
        
        # 최근 레코드 확인
        echo -e "  최근 레코드 확인 중..."
        shard_iterator=$(aws kinesis get-shard-iterator --stream-name ${stream} --shard-id shardId-000000000000 --shard-iterator-type LATEST --region ${AWS_REGION} --query 'ShardIterator' --output text 2>/dev/null || echo "")
        if [ -n "$shard_iterator" ]; then
            record_count=$(aws kinesis get-records --shard-iterator "$shard_iterator" --region ${AWS_REGION} --query 'Records | length(@)' --output text 2>/dev/null || echo "0")
            echo -e "  최근 레코드: ${record_count}개"
        fi
    else
        echo -e "  상태: ${RED}NOT_FOUND${NC}"
        echo -e "  ${YELLOW}💡 해결방법: ./setup-fluent-bit-iam.sh 실행${NC}"
    fi
done

# 6. IAM 권한 확인
print_section "6. IAM 권한 확인"

print_subsection "IAM Role 확인"
role_name="FluentBitKinesisRole"
if aws iam get-role --role-name ${role_name} >/dev/null 2>&1; then
    echo -e "${GREEN}✅ IAM Role ${role_name}이 존재합니다.${NC}"
    
    # Role에 연결된 Policy 확인
    echo -e "  연결된 Policy:"
    aws iam list-attached-role-policies --role-name ${role_name} --query 'AttachedPolicies[].PolicyName' --output text | tr '\t' '\n' | sed 's/^/    /'
else
    echo -e "${RED}❌ IAM Role ${role_name}이 존재하지 않습니다.${NC}"
    echo -e "${YELLOW}💡 해결방법: ./setup-fluent-bit-iam.sh 실행${NC}"
fi

# 7. 권장 해결책
print_section "7. 권장 해결책"

echo -e "${YELLOW}🔧 일반적인 문제 해결책:${NC}"
echo ""
echo -e "${BLUE}1. Pod가 Pending 상태인 경우:${NC}"
echo -e "   • kubectl describe pod <pod-name> -n ${NAMESPACE}"
echo -e "   • Fargate Profile 확인"
echo -e "   • 리소스 제한 확인"
echo ""
echo -e "${BLUE}2. Fluent Bit 연결 실패:${NC}"
echo -e "   • IAM Role 권한 확인"
echo -e "   • Kinesis Stream 존재 확인"
echo -e "   • AWS 리전 설정 확인"
echo ""
echo -e "${BLUE}3. 로그 파일 미생성:${NC}"
echo -e "   • LOG_PATH 환경변수 확인"
echo -e "   • 애플리케이션 재시작"
echo -e "   • Logback 설정 확인"
echo ""
echo -e "${BLUE}4. 추천 로그 미생성:${NC}"
echo -e "   • LOGGING_RECOMMENDATION_ENABLED=true 설정"
echo -e "   • 추천 이벤트 API 호출 테스트"

print_section "진단 완료"
echo -e "${GREEN}🎉 진단이 완료되었습니다!${NC}"
echo ""
echo -e "${YELLOW}📝 추가 도움이 필요한 경우:${NC}"
echo -e "  • kubectl describe pod <pod-name> -n ${NAMESPACE}"
echo -e "  • kubectl logs <pod-name> -n ${NAMESPACE} -c <container-name>"
echo -e "  • AWS CloudWatch Logs 확인"
echo -e "  • ./quick-start.sh 재실행"
