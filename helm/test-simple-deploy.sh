#!/bin/bash

echo "🧪 Helm 차트 간단 테스트 배포 시작..."

# 환경 변수 설정
NAMESPACE=${NAMESPACE:-dev}
ENVIRONMENT=${ENVIRONMENT:-dev}

echo "📋 테스트 정보:"
echo "  - Namespace: $NAMESPACE"
echo "  - Environment: $ENVIRONMENT"

# Namespace 생성
echo "📝 Namespace 생성 중..."
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

# 테스트용으로 admin-service만 배포
echo "🚀 admin-service만 테스트 배포 중..."
helm upgrade --install eatcloud-test ./eatcloud-apps \
  --namespace $NAMESPACE \
  --set global.environment=$ENVIRONMENT \
  --set global.namespace=$NAMESPACE \
  --set services.adminService.enabled=true \
  --set services.customerService.enabled=false \
  --set services.orderService.enabled=false \
  --set services.deliveryService.enabled=false \
  --wait --timeout=300s

if [ $? -eq 0 ]; then
    echo "✅ 테스트 배포 완료!"
    
    echo "📊 배포 상태 확인:"
    helm list -n $NAMESPACE
    kubectl get pods -n $NAMESPACE
    
    echo ""
    echo "🔍 로그 확인:"
    echo "  kubectl logs -n $NAMESPACE -l app=admin-service -c admin-service"
    echo "  kubectl logs -n $NAMESPACE -l app=admin-service -c fluent-bit"
else
    echo "❌ 테스트 배포 실패!"
    exit 1
fi
