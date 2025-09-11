#!/bin/bash

echo "🚀 EatCloud MSA Fluent Bit Helm 배포 시작..."

# 환경 변수 설정
NAMESPACE=${NAMESPACE:-dev}
ENVIRONMENT=${ENVIRONMENT:-dev}
RELEASE_NAME=${RELEASE_NAME:-fluent-bit}

echo "📋 배포 정보:"
echo "  - Namespace: $NAMESPACE"
echo "  - Environment: $ENVIRONMENT" 
echo "  - Release Name: $RELEASE_NAME"

# Namespace 생성
echo "📝 Namespace 생성 중..."
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

# Helm 차트 배포
echo "🚀 Helm 차트 배포 중..."
helm upgrade --install $RELEASE_NAME ./helm/fluent-bit \
  --namespace $NAMESPACE \
  --values ./helm/fluent-bit/values.yaml \
  --values ./helm/fluent-bit/values-${ENVIRONMENT}.yaml \
  --wait --timeout=300s

if [ $? -eq 0 ]; then
    echo "✅ Fluent Bit Helm 차트 배포 완료!"
    
    echo "📊 배포된 리소스 확인:"
    helm list -n $NAMESPACE
    kubectl get pods -n $NAMESPACE -l app=fluent-bit
    
    echo "🔍 로그 확인 명령어:"
    echo "  helm status $RELEASE_NAME -n $NAMESPACE"
    echo "  kubectl logs -n $NAMESPACE -l app=fluent-bit"
else
    echo "❌ Helm 차트 배포 실패!"
    exit 1
fi
