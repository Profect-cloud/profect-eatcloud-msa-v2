#!/bin/bash

echo "🚀 EatCloud MSA Helm 통합 배포 시작..."

# 환경 변수 설정
NAMESPACE=${NAMESPACE:-dev}
ENVIRONMENT=${ENVIRONMENT:-dev}

echo "📋 배포 정보:"
echo "  - Namespace: $NAMESPACE"
echo "  - Environment: $ENVIRONMENT"

# Namespace 생성
echo "📝 Namespace 생성 중..."
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

# 1. Fluent Bit 독립 배포 (DaemonSet 방식)
echo "🔧 1. Fluent Bit DaemonSet 배포 중..."
helm upgrade --install fluent-bit-daemon ./fluent-bit \
  --namespace $NAMESPACE \
  --set deployment.type=daemonset \
  --set fluentBit.environment=$ENVIRONMENT \
  --wait --timeout=300s

if [ $? -eq 0 ]; then
    echo "✅ Fluent Bit DaemonSet 배포 완료!"
else
    echo "❌ Fluent Bit DaemonSet 배포 실패!"
    exit 1
fi

# 2. EatCloud 애플리케이션 배포 (Sidecar 방식)
echo "🚀 2. EatCloud 애플리케이션 배포 중..."
helm upgrade --install eatcloud-apps ./eatcloud-apps \
  --namespace $NAMESPACE \
  --set global.environment=$ENVIRONMENT \
  --set global.namespace=$NAMESPACE \
  --wait --timeout=600s

if [ $? -eq 0 ]; then
    echo "✅ EatCloud 애플리케이션 배포 완료!"
else
    echo "❌ EatCloud 애플리케이션 배포 실패!"
    exit 1
fi

# 배포 상태 확인
echo "📊 배포 상태 확인:"
echo ""
echo "🔍 Helm 릴리스:"
helm list -n $NAMESPACE

echo ""
echo "📦 Pod 상태:"
kubectl get pods -n $NAMESPACE

echo ""
echo "🌐 서비스 상태:"
kubectl get svc -n $NAMESPACE

echo ""
echo "🔍 로그 확인 명령어:"
echo "  • kubectl logs -n $NAMESPACE -l app=admin-service -c fluent-bit"
echo "  • kubectl logs -n $NAMESPACE -l app=customer-service -c admin-service"
echo "  • helm status eatcloud-apps -n $NAMESPACE"

echo ""
echo "✨ EatCloud MSA Helm 통합 배포가 완료되었습니다!"
