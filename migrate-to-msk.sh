#!/bin/bash

# MSK 연동을 위한 애플리케이션 마이그레이션 스크립트

set -e

REGION="ap-northeast-2"
NAMESPACE="dev"
CLUSTER_NAME="eatcloud"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== MSK 연동을 위한 애플리케이션 마이그레이션 시작 ===${NC}"

# 1. 현재 kubectl 컨텍스트 확인
echo -e "${YELLOW}Step 1: kubectl 컨텍스트 확인${NC}"
kubectl config current-context
kubectl get nodes

# 2. MSK 클러스터 정보 확인
echo -e "${YELLOW}Step 2: MSK 클러스터 정보 확인${NC}"
MSK_ARN=$(aws kafka list-clusters --region $REGION --query 'ClusterInfoList[?ClusterName==`eatcloud-msk-kraft`].ClusterArn' --output text)
echo "MSK Cluster ARN: $MSK_ARN"

BOOTSTRAP_BROKERS=$(aws kafka get-bootstrap-brokers --cluster-arn "$MSK_ARN" --region $REGION --query '