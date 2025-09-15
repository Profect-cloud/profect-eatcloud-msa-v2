#!/bin/bash

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== EatCloud 로그 파이프라인 Lambda 설정 ===${NC}"
echo -e "${YELLOW}Stateless & Stateful 로그 → Loki${NC}"
echo ""

# 변수 설정
REGION="ap-northeast-2"
ACCOUNT_ID="536580887516"
VPC_ID="vpc-0bdbee988c0d5e2cc"
SUBNET_IDS="subnet-029b4e47d0be0c4b5,subnet-0c66ca1fea24116a5"
SECURITY_GROUP="sg-0a5b2954e49888013"

# 1. IAM Role 생성
echo -e "${GREEN}1. Lambda IAM Role 생성...${NC}"

cat > lambda-trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

# Role 생성
aws iam create-role \
    --role-name lambda-kinesis-to-loki-role \
    --assume-role-policy-document file://lambda-trust-policy.json \
    --region $REGION 2>/dev/null || echo "Role already exists"

# Policy 연결
aws iam attach-role-policy \
    --role-name lambda-kinesis-to-loki-role \
    --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaKinesisExecutionRole \
    --region $REGION 2>/dev/null

aws iam attach-role-policy \
    --role-name lambda-kinesis-to-loki-role \
    --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole \
    --region $REGION 2>/dev/null

ROLE_ARN=$(aws iam get-role --role-name lambda-kinesis-to-loki-role --query 'Role.Arn' --output text)
echo -e "${GREEN}   Role ARN: $ROLE_ARN${NC}"

# 2. Lambda 코드 패키징
echo -e "${GREEN}2. Lambda 패키지 생성...${NC}"
rm -rf package
mkdir package
python3 -m pip install requests -t package/ --quiet
cp lambda-stateless-logs.py package/lambda_function.py
cd package
zip -r ../lambda-logs-to-loki.zip . -q
cd ..
rm -rf package

# 3. Loki 엔드포인트 확인/생성
echo -e "${GREEN}3. Loki LoadBalancer 확인...${NC}"
LOKI_LB=$(kubectl get svc loki-external -n monitoring -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null)

if [ -z "$LOKI_LB" ]; then
    echo -e "${YELLOW}   Loki LoadBalancer 생성 중...${NC}"
    kubectl apply -f 4-loki-loadbalancer.yaml

    # 대기
    for i in {1..36}; do
        LOKI_LB=$(kubectl get svc loki-external -n monitoring -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null)
        if [ ! -z "$LOKI_LB" ]; then
            break
        fi
        echo -n "."
        sleep 5
    done
fi

if [ -z "$LOKI_LB" ]; then
    echo -e "${RED}❌ LoadBalancer 생성 실패${NC}"
    echo -e "${YELLOW}기본값 사용: loki.monitoring.svc.cluster.local${NC}"
    LOKI_ENDPOINT="http://loki.monitoring.svc.cluster.local:3100"
else
    LOKI_ENDPOINT="http://$LOKI_LB:3100"
fi

echo -e "${GREEN}   Loki Endpoint: $LOKI_ENDPOINT${NC}"

# 4. Stateless 로그용 Lambda 함수 생성/업데이트
echo -e "${GREEN}4. Stateless 로그 Lambda 함수 설정...${NC}"
FUNCTION_NAME="kinesis-stateless-to-loki"

# 함수 존재 확인
if aws lambda get-function --function-name $FUNCTION_NAME --region $REGION 2>/dev/null; then
    echo -e "${YELLOW}   기존 함수 업데이트${NC}"
    aws lambda update-function-code \
        --function-name $FUNCTION_NAME \
        --zip-file fileb://lambda-logs-to-loki.zip \
        --region $REGION > /dev/null

    # 함수가 완전히 업데이트될 때까지 대기
    echo -e "${YELLOW}   함수 업데이트 대기 중...${NC}"
    aws lambda wait function-updated --function-name $FUNCTION_NAME --region $REGION

else
    echo -e "${YELLOW}   새 함수 생성${NC}"
    aws lambda create-function \
        --function-name $FUNCTION_NAME \
        --runtime python3.9 \
        --role $ROLE_ARN \
        --handler lambda_function.lambda_handler \
        --zip-file fileb://lambda-logs-to-loki.zip \
        --timeout 60 \
        --memory-size 512 \
        --environment "Variables={LOKI_ENDPOINT=$LOKI_ENDPOINT,LOG_TYPE=stateless}" \
        --region $REGION > /dev/null

    # 함수가 완전히 생성될 때까지 대기
    echo -e "${YELLOW}   함수 활성화 대기 중...${NC}"
    aws lambda wait function-active --function-name $FUNCTION_NAME --region $REGION
fi

# VPC 설정
aws lambda update-function-configuration \
    --function-name $FUNCTION_NAME \
    --vpc-config SubnetIds=$SUBNET_IDS,SecurityGroupIds=$SECURITY_GROUP \
    --region $REGION > /dev/null

# Event Source Mapping
MAPPING=$(aws lambda list-event-source-mappings \
    --function-name $FUNCTION_NAME \
    --region $REGION \
    --query "EventSourceMappings[?contains(EventSourceArn, 'stateless-logs')]" \
    --output json)

if [ "$MAPPING" == "[]" ]; then
    aws lambda create-event-source-mapping \
        --function-name $FUNCTION_NAME \
        --event-source-arn arn:aws:kinesis:$REGION:$ACCOUNT_ID:stream/eatcloud-stateless-logs \
        --starting-position LATEST \
        --batch-size 100 \
        --maximum-batching-window-in-seconds 5 \
        --region $REGION > /dev/null
    echo -e "${GREEN}   ✅ Stateless 로그 Lambda 설정 완료${NC}"
fi

# 5. Stateful 로그용 Lambda 함수 생성/업데이트
echo -e "${GREEN}5. Stateful 로그 Lambda 함수 설정...${NC}"
FUNCTION_NAME="kinesis-stateful-to-loki"

if aws lambda get-function --function-name $FUNCTION_NAME --region $REGION 2>/dev/null; then
    echo -e "${YELLOW}   기존 함수 업데이트${NC}"
    aws lambda update-function-code \
        --function-name $FUNCTION_NAME \
        --zip-file fileb://lambda-logs-to-loki.zip \
        --region $REGION > /dev/null

    # 함수가 완전히 업데이트될 때까지 대기
    echo -e "${YELLOW}   함수 업데이트 대기 중...${NC}"
    aws lambda wait function-updated --function-name $FUNCTION_NAME --region $REGION
else
    echo -e "${YELLOW}   새 함수 생성${NC}"
    aws lambda create-function \
        --function-name $FUNCTION_NAME \
        --runtime python3.9 \
        --role $ROLE_ARN \
        --handler lambda_function.lambda_handler \
        --zip-file fileb://lambda-logs-to-loki.zip \
        --timeout 60 \
        --memory-size 512 \
        --environment "Variables={LOKI_ENDPOINT=$LOKI_ENDPOINT,LOG_TYPE=stateful}" \
        --region $REGION > /dev/null

    # 함수가 완전히 생성될 때까지 대기
    echo -e "${YELLOW}   함수 활성화 대기 중...${NC}"
    aws lambda wait function-active --function-name $FUNCTION_NAME --region $REGION
fi

# VPC 설정
aws lambda update-function-configuration \
    --function-name $FUNCTION_NAME \
    --vpc-config SubnetIds=$SUBNET_IDS,SecurityGroupIds=$SECURITY_GROUP \
    --region $REGION > /dev/null

# Event Source Mapping
MAPPING=$(aws lambda list-event-source-mappings \
    --function-name $FUNCTION_NAME \
    --region $REGION \
    --query "EventSourceMappings[?contains(EventSourceArn, 'stateful-logs')]" \
    --output json)

if [ "$MAPPING" == "[]" ]; then
    aws lambda create-event-source-mapping \
        --function-name $FUNCTION_NAME \
        --event-source-arn arn:aws:kinesis:$REGION:$ACCOUNT_ID:stream/eatcloud-stateful-logs \
        --starting-position LATEST \
        --batch-size 100 \
        --maximum-batching-window-in-seconds 5 \
        --region $REGION > /dev/null
    echo -e "${GREEN}   ✅ Stateful 로그 Lambda 설정 완료${NC}"
fi

# 6. 정리
rm -f lambda-trust-policy.json lambda-logs-to-loki.zip

echo ""
echo -e "${GREEN}✅ 모든 Lambda 함수 설정 완료!${NC}"
echo ""
echo -e "${BLUE}=== 설정 정보 ===${NC}"
echo -e "Stateless Lambda: ${YELLOW}kinesis-stateless-to-loki${NC}"
echo -e "Stateful Lambda: ${YELLOW}kinesis-stateful-to-loki${NC}"
echo -e "Loki Endpoint: ${YELLOW}$LOKI_ENDPOINT${NC}"
echo ""
echo -e "${BLUE}=== 모니터링 명령어 ===${NC}"
echo "# Stateless 로그 확인"
echo "aws logs tail /aws/lambda/kinesis-stateless-to-loki --follow"
echo ""
echo "# Stateful 로그 확인"
echo "aws logs tail /aws/lambda/kinesis-stateful-to-loki --follow"
echo ""
echo "# Loki 상태 확인"
echo "kubectl logs -n monitoring loki-0 -f"
echo ""
echo "# Grafana 접속"
echo "kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80"
echo ""
echo -e "${YELLOW}참고: Recommendation 이벤트는 MongoDB로만 전송됩니다 (Loki X)${NC}"