#!/bin/bash

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== AWS CLI를 사용하여 VPC 및 보안 그룹 설정 ===${NC}"

# 1. EKS 클러스터의 VPC ID 가져오기
echo -e "${YELLOW}1. EKS 클러스터의 VPC ID 확인...${NC}"
VPC_ID=$(aws eks describe-cluster \
    --name eatcloud \
    --region ap-northeast-2 \
    --query 'cluster.resourcesVpcConfig.vpcId' \
    --output text)

if [ -z "$VPC_ID" ] || [ "$VPC_ID" == "null" ]; then
    echo -e "${RED}❌ EKS 클러스터의 VPC ID를 찾을 수 없습니다. 클러스터 이름을 확인해주세요.${NC}"
    exit 1
fi
echo -e "${GREEN}   ✅ VPC ID: $VPC_ID${NC}"

# 2. Lambda 함수 보안 그룹 ID 가져오기
echo -e "${YELLOW}2. Lambda 함수 보안 그룹 ID 확인...${NC}"
LAMBDA_SG_ID=$(aws lambda get-function-configuration \
    --function-name kinesis-stateless-to-loki \
    --region ap-northeast-2 \
    --query 'VpcConfig.SecurityGroupIds[0]' \
    --output text)

if [ -z "$LAMBDA_SG_ID" ] || [ "$LAMBDA_SG_ID" == "None" ]; then
    echo -e "${RED}❌ Lambda 함수 보안 그룹 ID를 찾을 수 없습니다.${NC}"
    exit 1
fi
echo -e "${GREEN}   ✅ Lambda SG ID: $LAMBDA_SG_ID${NC}"

# 3. 기존 Loki 로드 밸런서 보안 그룹 ID 가져오기
echo -e "${YELLOW}3. 기존 Loki 로드 밸런서 보안 그룹 ID 확인...${NC}"
LOKI_LB_SG=$(aws ec2 describe-security-groups \
    --region ap-northeast-2 \
    --filters "Name=group-name,Values=loki-lb-ingress-sg" \
    --query 'SecurityGroups[0].GroupId' \
    --output text)

if [ -z "$LOKI_LB_SG" ] || [ "$LOKI_LB_SG" == "None" ]; then
    echo -e "${RED}❌ 'loki-lb-ingress-sg' 보안 그룹을 찾을 수 없습니다. 수동으로 확인하거나 새로 생성해야 합니다.${NC}"
    exit 1
fi
echo -e "${GREEN}   ✅ Loki LB SG ID: $LOKI_LB_SG${NC}"

# 4. 기존 규칙 삭제 (재실행 시 중복 규칙 생성 방지)
echo -e "${YELLOW}4. 기존 규칙 정리 (재실행 시 중복 방지)...${NC}"
aws ec2 revoke-security-group-ingress \
    --group-id $LOKI_LB_SG \
    --region ap-northeast-2 \
    --protocol tcp \
    --port 3100 \
    --source-group $LAMBDA_SG_ID 2>/dev/null
echo -e "${GREEN}   ✅ Loki LB 인바운드 규칙 정리 완료${NC}"

aws ec2 revoke-security-group-egress \
    --group-id $LAMBDA_SG_ID \
    --region ap-northeast-2 \
    --ip-permissions 'IpProtocol=tcp,FromPort=3100,ToPort=3100,UserIdGroupPairs=[{GroupId='$LOKI_LB_SG'}]' 2>/dev/null
echo -e "${GREEN}   ✅ Lambda 아웃바운드 규칙 정리 완료${NC}"


# 5. 새 인바운드 규칙 추가 (Lambda → Loki LB)
echo -e "${YELLOW}5. 인바운드 규칙 추가 (Lambda → Loki)...${NC}"
aws ec2 authorize-security-group-ingress \
    --group-id $LOKI_LB_SG \
    --protocol tcp \
    --port 3100 \
    --source-group $LAMBDA_SG_ID \
    --region ap-northeast-2
echo -e "${GREEN}   ✅ 인바운드 규칙 추가 완료: TCP 3100 포트, 소스 = Lambda SG${NC}"

# 6. 아웃바운드 규칙 추가 (Loki → Lambda)
echo -e "${YELLOW}6. 아웃바운드 규칙 추가 (Loki → Lambda)...${NC}"
aws ec2 authorize-security-group-egress \
    --group-id $LAMBDA_SG_ID \
    --region ap-northeast-2 \
    --ip-permissions 'IpProtocol=tcp,FromPort=3100,ToPort=3100,UserIdGroupPairs=[{GroupId='$LOKI_LB_SG'}]'
echo -e "${GREEN}   ✅ 아웃바운드 규칙 추가 완료: TCP 3100 포트, 목적지 = Loki LB SG${NC}"

# 7. Loki 로드 밸런서에 새 보안 그룹 연결
echo -e "${YELLOW}7. Loki 로드 밸런서에 새 보안 그룹 연결...${NC}"
LOKI_LB_ARN=$(aws elbv2 describe-load-balancers \
    --region ap-northeast-2 \
    --query "LoadBalancers[?contains(DNSName, 'k8s-monitori-lokiexte')].LoadBalancerArn" \
    --output text)

if [ -z "$LOKI_LB_ARN" ] || [ "$LOKI_LB_ARN" == "None" ]; then
    echo -e "${RED}❌ Loki 로드 밸런서 ARN을 찾을 수 없습니다. 수동으로 확인해주세요.${NC}"
    exit 1
fi

aws elbv2 set-security-groups \
    --load-balancer-arn $LOKI_LB_ARN \
    --security-groups $LOKI_LB_SG \
    --region ap-northeast-2
echo -e "${GREEN}   ✅ Loki 로드 밸런서에 보안 그룹 연결 완료${NC}"

echo ""
echo -e "${GREEN}✅ 모든 보안 그룹 설정이 완료되었습니다!${NC}"
echo -e "${YELLOW}이제 몇 분 후 Lambda 함수가 Loki에 접속할 수 있는지 확인하세요.${NC}"