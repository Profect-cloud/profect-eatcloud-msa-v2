#!/bin/bash

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Lambda → Loki 직접 전송 설정 ===${NC}"
echo ""

# 변수 설정
FUNCTION_NAME="kinesis-to-loki"
REGION="ap-northeast-2"
VPC_ID="vpc-0bdbee988c0d5e2cc"
SUBNET_IDS="subnet-029b4e47d0be0c4b5,subnet-0c66ca1fea24116a5"
SECURITY_GROUP="sg-0a5b2954e49888013"

# 1. Lambda 코드 패키징
echo -e "${GREEN}1. Lambda 함수 패키징...${NC}"
pip install requests -t package/ --upgrade -q
cp lambda_function.py package/
cd package
zip -r ../lambda-kinesis-to-loki.zip . -q
cd ..
rm -rf package/

echo -e "${GREEN}✅ 패키징 완료${NC}"

# 2. Lambda 함수 업데이트 또는 생성
echo -e "${GREEN}2. Lambda 함수 확인...${NC}"
if aws lambda get-function --function-name $FUNCTION_NAME --region $REGION 2>/dev/null; then
    echo -e "${YELLOW}   기존 함수 업데이트${NC}"
    aws lambda update-function-code \
        --function-name $FUNCTION_NAME \
        --zip-file fileb://lambda-kinesis-to-loki.zip \
        --region $REGION > /dev/null
else
    echo -e "${YELLOW}   새 함수 생성${NC}"
    # IAM Role 먼저 생성 필요
    echo -e "${RED}   Lambda 함수가 없습니다. 먼저 생성해주세요.${NC}"
    exit 1
fi

# 3. NLB 생성 (Loki 접근용)
echo -e "${GREEN}3. Loki LoadBalancer 확인...${NC}"
LOKI_LB=$(kubectl get svc loki-external -n monitoring -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null)

if [ -z "$LOKI_LB" ]; then
    echo -e "${YELLOW}   Loki LoadBalancer 생성 중...${NC}"
    kubectl apply -f 4-loki-loadbalancer.yaml
    
    # LoadBalancer 생성 대기
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
    exit 1
fi

echo -e "${GREEN}✅ Loki 엔드포인트: http://$LOKI_LB:3100${NC}"

# 4. Lambda 환경 변수 설정
echo -e "${GREEN}4. Lambda 환경 변수 설정...${NC}"

# S3 백업 옵션
echo -e "${YELLOW}S3 백업을 활성화하시겠습니까? (y/n)${NC}"
read -r s3_backup

if [[ "$s3_backup" =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}S3 버킷 이름을 입력하세요:${NC}"
    read -r bucket_name
    
    # 버킷 생성
    aws s3api create-bucket \
        --bucket $bucket_name \
        --region $REGION \
        --create-bucket-configuration LocationConstraint=$REGION 2>/dev/null || echo "Bucket exists"
    
    ENV_VARS="LOKI_ENDPOINT=http://$LOKI_LB:3100,ENABLE_S3_BACKUP=true,S3_BUCKET=$bucket_name"
else
    ENV_VARS="LOKI_ENDPOINT=http://$LOKI_LB:3100,ENABLE_S3_BACKUP=false"
fi

aws lambda update-function-configuration \
    --function-name $FUNCTION_NAME \
    --environment Variables={$ENV_VARS} \
    --timeout 60 \
    --memory-size 512 \
    --region $REGION > /dev/null

# 5. Lambda VPC 설정
echo -e "${GREEN}5. Lambda VPC 설정...${NC}"
aws lambda update-function-configuration \
    --function-name $FUNCTION_NAME \
    --vpc-config SubnetIds=$SUBNET_IDS,SecurityGroupIds=$SECURITY_GROUP \
    --region $REGION > /dev/null

# 6. 함수 활성화 대기
echo -e "${YELLOW}6. Lambda 함수 활성화 대기 중...${NC}"
for i in {1..12}; do
    STATUS=$(aws lambda get-function-configuration \
        --function-name $FUNCTION_NAME \
        --region $REGION \
        --query 'State' \
        --output text)
    
    if [ "$STATUS" == "Active" ]; then
        echo -e "${GREEN}✅ Lambda 함수 준비 완료!${NC}"
        break
    fi
    echo -n "."
    sleep 5
done

# 7. Event Source Mapping 확인
echo -e "${GREEN}7. Kinesis 트리거 확인...${NC}"
MAPPINGS=$(aws lambda list-event-source-mappings \
    --function-name $FUNCTION_NAME \
    --region $REGION \
    --query 'EventSourceMappings[?EventSourceArn==`arn:aws:kinesis:ap-northeast-2:536580887516:stream/eatcloud-logs`]' \
    --output json)

if [ "$MAPPINGS" == "[]" ]; then
    echo -e "${YELLOW}   Kinesis 트리거 생성 중...${NC}"
    aws lambda create-event-source-mapping \
        --function-name $FUNCTION_NAME \
        --event-source-arn arn:aws:kinesis:$REGION:536580887516:stream/eatcloud-logs \
        --starting-position LATEST \
        --region $REGION > /dev/null
else
    echo -e "${GREEN}   Kinesis 트리거 이미 존재${NC}"
fi

echo ""
echo -e "${GREEN}✅ 모든 설정 완료!${NC}"
echo ""
echo -e "${BLUE}=== 확인 명령어 ===${NC}"
echo "# Lambda 로그 확인"
echo "aws logs tail /aws/lambda/$FUNCTION_NAME --follow"
echo ""
echo "# Loki 로그 확인"
echo "kubectl logs -n monitoring loki-0 -f"
echo ""
echo "# Grafana 접속"
echo "kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80"
echo ""
echo -e "${YELLOW}Grafana에서 Loki 데이터소스 추가:${NC}"
echo "URL: http://$LOKI_LB:3100"
