#!/bin/bash

# Loki 연결 문제 진단 스크립트

echo "=== Loki 연결 문제 진단 ==="

LOKI_ENDPOINT="http://k8s-dev-eatcloud-600fc1a967-383401301.ap-northeast-2.elb.amazonaws.com:3100"

echo "1. EKS 클러스터 연결 확인"
kubectl config current-context

echo -e "\n2. Loki 서비스 상태 확인"
kubectl get svc -n logging 2>/dev/null || {
    echo "logging 네임스페이스가 없습니다. 다른 네임스페이스 확인:"
    kubectl get svc -A | grep -i loki
}

echo -e "\n3. Loki Pod 상태 확인" 
kubectl get pods -n logging 2>/dev/null || {
    echo "logging 네임스페이스가 없습니다. 다른 네임스페이스에서 Loki 찾기:"
    kubectl get pods -A | grep -i loki
}

echo -e "\n4. LoadBalancer 서비스 확인"
kubectl get svc -A | grep LoadBalancer

echo -e "\n5. 현재 Loki 엔드포인트 연결 테스트"
echo "엔드포인트: $LOKI_ENDPOINT"

# DNS 해결 확인
echo -e "\nDNS 해결 확인:"
nslookup k8s-dev-eatcloud-600fc1a967-383401301.ap-northeast-2.elb.amazonaws.com || echo "DNS 해결 실패"

# 포트 연결 확인
echo -e "\n포트 연결 확인:"
nc -z -v k8s-dev-eatcloud-600fc1a967-383401301.ap-northeast-2.elb.amazonaws.com 3100 2>&1 || echo "포트 3100 연결 실패"

# HTTP 응답 확인 (타임아웃 5초)
echo -e "\nHTTP 연결 테스트:"
curl -v --connect-timeout 5 --max-time 10 "${LOKI_ENDPOINT}/ready" 2>&1 | head -20

echo -e "\n=== 해결 방법 제안 ==="

echo "6. Loki가 실제로 실행 중인지 확인"
echo "다음 명령어들을 실행해서 Loki 상태를 확인하세요:"
echo ""
echo "# 모든 네임스페이스에서 Loki 찾기"
echo "kubectl get pods -A | grep -i loki"
echo ""
echo "# Loki 서비스 찾기"  
echo "kubectl get svc -A | grep -i loki"
echo ""
echo "# Loki 설치 확인 (Helm)"
echo "helm list -A | grep -i loki"

echo -e "\n7. 가능한 문제들:"
echo "❌ Loki가 설치되지 않음"
echo "❌ LoadBalancer가 생성되지 않음" 
echo "❌ 보안 그룹에서 3100 포트 차단"
echo "❌ Loki가 다른 포트에서 실행 중"
echo "❌ 네트워크 연결 문제"

echo -e "\n8. LoadBalancer 대신 포트 포워딩 사용 (임시 해결책):"
echo "kubectl port-forward -n logging svc/loki 3100:3100"
echo "그러면 Lambda에서 http://localhost:3100 대신:"
echo "kubectl get nodes -o wide 로 노드 IP를 확인하고"
echo "http://NODE_IP:3100 사용"

echo -e "\n먼저 위의 명령어들을 실행해서 Loki 상태를 확인해주세요! 🔍"
