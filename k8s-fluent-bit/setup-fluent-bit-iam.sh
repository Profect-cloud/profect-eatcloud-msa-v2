#!/bin/bash

# 🚀 EatCloud Fluent Bit IAM & Kinesis 설정 스크립트
# 
# 이 스크립트는 다음을 설정합니다:
# 1. Fluent Bit용 IAM Role 및 Policy
# 2. 3개 Kinesis Data Streams 
# 3. EKS Service Account 연결

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 변수 정의
AWS_REGION="ap-northeast-2"
AWS_ACCOUNT_ID="536580887516"
CLUSTER_NAME="eatcloud"
NAMESPACE="dev"
SERVICE_ACCOUNT_NAME="fluent-bit-service-account"
ROLE_NAME="FluentBitKinesisRole"
POLICY_NAME="FluentBitKinesisPolicy"

# Kinesis Streams
STATEFUL_STREAM="eatcloud-stateful-logs"
STATELESS_STREAM="eatcloud-stateless-logs"
RECOMMENDATION_STREAM="eatcloud-recommendation-events"

echo -e "${BLUE}🚀 EatCloud Fluent Bit 설정을 시작합니다...${NC}"

# 1. IAM Policy 생성
echo -e "${YELLOW}📋 IAM Policy 생성 중...${NC}"
cat > fluent-bit-kinesis-policy.json << EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "kinesis:DescribeStream",
                "kinesis:PutRecord",
                "kinesis:PutRecords",
                "kinesis:ListStreams"
            ],
            "Resource": [
                "arn:aws:kinesis:${AWS_REGION}:${AWS_ACCOUNT_ID}:stream/${STATEFUL_STREAM}",
                "arn:aws:kinesis:${AWS_REGION}:${AWS_ACCOUNT_ID}:stream/${STATELESS_STREAM}",
                "arn:aws:kinesis:${AWS_REGION}:${AWS_ACCOUNT_ID}:stream/${RECOMMENDATION_STREAM}"
            ]
        },
        {
            "Effect": "Allow",
            "Action": [
                "logs:CreateLogGroup",
                "logs:CreateLogStream",
                "logs:PutLogEvents",
                "logs:DescribeLogStreams"
            ],
            "Resource": "*"
        }
    ]
}
EOF

# Policy 생성 또는 업데이트
if aws iam get-policy --policy-arn "arn:aws:iam::${AWS_ACCOUNT_ID}:policy/${POLICY_NAME}" >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  Policy가 이미 존재합니다. 새 버전을 생성합니다...${NC}"
    aws iam create-policy-version \
        --policy-arn "arn:aws:iam::${AWS_ACCOUNT_ID}:policy/${POLICY_NAME}" \
        --policy-document file://fluent-bit-kinesis-policy.json \
        --set-as-default
else
    echo -e "${GREEN}✅ 새 Policy를 생성합니다...${NC}"
    aws iam create-policy \
        --policy-name ${POLICY_NAME} \
        --policy-document file://fluent-bit-kinesis-policy.json \
        --description "Policy for Fluent Bit to access Kinesis streams"
fi

# 2. Trust Policy 생성
echo -e "${YELLOW}🔐 Trust Policy 생성 중...${NC}"
cat > trust-policy.json << EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "Federated": "arn:aws:iam::${AWS_ACCOUNT_ID}:oidc-provider/oidc.eks.${AWS_REGION}.amazonaws.com/id/D9E5C3E5E3E5C3E5C3E5C3E5C3E5C3E5"
            },
            "Action": "sts:AssumeRoleWithWebIdentity",
            "Condition": {
                "StringEquals": {
                    "oidc.eks.${AWS_REGION}.amazonaws.com/id/D9E5C3E5E3E5C3E5C3E5C3E5C3E5C3E5:sub": "system:serviceaccount:${NAMESPACE}:${SERVICE_ACCOUNT_NAME}",
                    "oidc.eks.${AWS_REGION}.amazonaws.com/id/D9E5C3E5E3E5C3E5C3E5C3E5C3E5C3E5:aud": "sts.amazonaws.com"
                }
            }
        }
    ]
}
EOF

# OIDC Provider URL 가져오기
echo -e "${YELLOW}🔍 EKS OIDC Provider 확인 중...${NC}"
OIDC_ISSUER=$(aws eks describe-cluster --name ${CLUSTER_NAME} --region ${AWS_REGION} --query "cluster.identity.oidc.issuer" --output text)
OIDC_ID=$(echo ${OIDC_ISSUER} | cut -d '/' -f 5)

# Trust Policy 업데이트 (macOS 호환)
if [[ "$OSTYPE" == "darwin"* ]]; then
    sed -i '' "s/D9E5C3E5E3E5C3E5C3E5C3E5C3E5C3E5/${OIDC_ID}/g" trust-policy.json
else
    sed -i "s/D9E5C3E5E3E5C3E5C3E5C3E5C3E5C3E5/${OIDC_ID}/g" trust-policy.json
fi

# 3. IAM Role 생성
echo -e "${YELLOW}👤 IAM Role 생성 중...${NC}"
if aws iam get-role --role-name ${ROLE_NAME} >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  Role이 이미 존재합니다. Trust Policy를 업데이트합니다...${NC}"
    aws iam update-assume-role-policy \
        --role-name ${ROLE_NAME} \
        --policy-document file://trust-policy.json
else
    echo -e "${GREEN}✅ 새 Role을 생성합니다...${NC}"
    aws iam create-role \
        --role-name ${ROLE_NAME} \
        --assume-role-policy-document file://trust-policy.json \
        --description "Role for Fluent Bit to access Kinesis streams"
fi

# 4. Policy를 Role에 연결
echo -e "${YELLOW}🔗 Policy를 Role에 연결 중...${NC}"
aws iam attach-role-policy \
    --role-name ${ROLE_NAME} \
    --policy-arn "arn:aws:iam::${AWS_ACCOUNT_ID}:policy/${POLICY_NAME}"

# 5. Kinesis Data Streams 생성
echo -e "${YELLOW}🌊 Kinesis Data Streams 생성 중...${NC}"

create_kinesis_stream() {
    local stream_name=$1
    local shard_count=$2
    
    if aws kinesis describe-stream --stream-name ${stream_name} --region ${AWS_REGION} >/dev/null 2>&1; then
        echo -e "${YELLOW}⚠️  Stream ${stream_name}이 이미 존재합니다.${NC}"
    else
        echo -e "${GREEN}✅ Stream ${stream_name}을 생성합니다...${NC}"
        aws kinesis create-stream \
            --stream-name ${stream_name} \
            --shard-count ${shard_count} \
            --region ${AWS_REGION}
        
        echo -e "${BLUE}⏳ Stream ${stream_name}이 활성화될 때까지 대기 중...${NC}"
        aws kinesis wait stream-exists --stream-name ${stream_name} --region ${AWS_REGION}
    fi
}

# Streams 생성
create_kinesis_stream ${STATEFUL_STREAM} 2      # 상태 기반 로그용
create_kinesis_stream ${STATELESS_STREAM} 3     # 상태 비기반 로그용 (더 많은 데이터)
create_kinesis_stream ${RECOMMENDATION_STREAM} 1 # 추천 이벤트용

# 6. 설정 파일 정리
echo -e "${YELLOW}🧹 임시 파일 정리 중...${NC}"
rm -f fluent-bit-kinesis-policy.json trust-policy.json

# 7. 설정 요약 출력
echo -e "${GREEN}🎉 설정이 완료되었습니다!${NC}"
echo -e "${BLUE}📋 설정 요약:${NC}"
echo -e "  • IAM Role: ${ROLE_NAME}"
echo -e "  • IAM Policy: ${POLICY_NAME}"
echo -e "  • Kinesis Streams:"
echo -e "    - ${STATEFUL_STREAM} (2 shards)"
echo -e "    - ${STATELESS_STREAM} (3 shards)"
echo -e "    - ${RECOMMENDATION_STREAM} (1 shard)"
echo ""
echo -e "${YELLOW}📝 다음 단계:${NC}"
echo -e "  1. ./quick-start.sh 실행하여 서비스 배포"
echo -e "  2. kubectl get pods -n dev 로 상태 확인"
echo -e "  3. ./troubleshoot.sh 로 문제 해결 (필요시)"

# 8. Kinesis Stream 상태 확인
echo -e "${BLUE}🔍 Kinesis Stream 상태 확인:${NC}"
for stream in ${STATEFUL_STREAM} ${STATELESS_STREAM} ${RECOMMENDATION_STREAM}; do
    status=$(aws kinesis describe-stream --stream-name ${stream} --region ${AWS_REGION} --query 'StreamDescription.StreamStatus' --output text)
    echo -e "  • ${stream}: ${status}"
done

echo -e "${GREEN}✨ 모든 설정이 완료되었습니다!${NC}"
