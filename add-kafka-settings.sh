#!/bin/bash

# MSK Kafka 추가 설정 패치 스크립트

set -e

NAMESPACE="dev"

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== MSK Kafka 추가 설정 적용 ===${NC}"

# 현재 ConfigMap 백업
echo -e "${YELLOW}Step 1: 현재 ConfigMap 백업${NC}"
kubectl get configmap complete-configmap -n $NAMESPACE -o yaml > configmap-backup-$(date +%Y%m%d-%H%M%S).yaml

# 추가 Kafka 설정이 필요한지 확인
echo -e "${YELLOW}Step 2: 현재 Kafka 설정 확인${NC}"
kubectl get configmap complete-configmap -n $NAMESPACE -o jsonpath='{.data.SPRING_KAFKA_CONSUMER_PROPERTIES_SPRING_JSON_USE_TYPE_HEADERS}' || echo "SPRING_KAFKA_CONSUMER_PROPERTIES_SPRING_JSON_USE_TYPE_HEADERS 없음"

# 누락된 설정 추가
echo -e "${YELLOW}Step 3: 누락된 Kafka 설정 추가${NC}"
kubectl patch configmap complete-configmap -n $NAMESPACE --type merge -p '{
  "data": {
    "SPRING_KAFKA_CONSUMER_PROPERTIES_SPRING_JSON_USE_TYPE_HEADERS": "false",
    "SPRING_KAFKA_PRODUCER_PROPERTIES_SPRING_JSON_TRUSTED_PACKAGES": "*",
    "SPRING_KAFKA_CONSUMER_GROUP_ID": "eatcloud-services",
    "SPRING_KAFKA_PRODUCER_RETRIES": "3",
    "SPRING_KAFKA_PRODUCER_ACKS": "all",
    "SPRING_KAFKA_PRODUCER_PROPERTIES_ENABLE_IDEMPOTENCE": "true"
  }
}'

# 설정 확인
echo -e "${YELLOW}Step 4: 업데이트된 Kafka 설정 확인${NC}"
echo "=== Kafka 관련 설정 ===="
kubectl get configmap complete-configmap -n $NAMESPACE -o yaml | grep -i kafka

# Kafka를 사용하는 서비스들 재시작
echo -e "${YELLOW}Step 5: Kafka 사용 서비스 재시작${NC}"
KAFKA_SERVICES=("order-service" "payment-service" "customer-service")

for service in "${KAFKA_SERVICES[@]}"; do
    echo -e "${BLUE}재시작 중: $service${NC}"
    kubectl rollout restart deployment $service -n $NAMESPACE
    echo "재시작 완료: $service"
done

echo -e "${GREEN}=== 설정 적용 완료 ===${NC}"
echo -e "${BLUE}서비스 상태 확인:${NC}"
for service in "${KAFKA_SERVICES[@]}"; do
    echo "kubectl rollout status deployment $service -n $NAMESPACE"
done

echo -e "${BLUE}로그 확인:${NC}"
for service in "${KAFKA_SERVICES[@]}"; do
    echo "kubectl logs -f deployment/$service -n $NAMESPACE"
done
