#!/bin/bash

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Loki External Access 설정 ===${NC}"
echo ""

# 1. LoadBalancer Service 생성
echo -e "${GREEN}1. LoadBalancer Service 생성...${NC}"
kubectl apply -f 4-loki-loadbalancer.yaml

# 2. LoadBalancer가 생성될 때까지 대기
echo -e "${YELLOW}2. NLB 생성 대기 중... (최대 3분)${NC}"
for i in {1..36}; do
    LOKI_LB=$(kubectl get svc loki-external -n monitoring -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null)
    if [ ! -z "$LOKI_LB" ]; then
        echo -e "${GREEN}✅ NLB 생성 완료!${NC}"
        echo -e "${BLUE}   주소: $LOKI_LB${NC}"
        break
    fi
    echo -n "."
    sleep 5
done

if [ -z "$LOKI_LB" ]; then
    echo -e "${RED}❌ NLB 생성 실패 또는 시간 초과${NC}"
    exit 1
fi

# 3. Loki 엔드포인트 테스트
echo ""
echo -e "${GREEN}3. Loki 엔드포인트 확인...${NC}"
LOKI_ENDPOINT="http://$LOKI_LB:3100"

# curl 대신 kubectl port-forward로 먼저 테스트
kubectl port-forward -n monitoring svc/loki-external 3100:3100 &
PF_PID=$!
sleep 3

if curl -s -o /dev/null -w "%{http_code}" http://localhost:3100/ready | grep -q "200"; then
    echo -e "${GREEN}✅ Loki가 정상적으로 응답합니다${NC}"
else
    echo -e "${YELLOW}⚠️  Loki 응답 확인 필요 (NLB 프로비저닝 중일 수 있음)${NC}"
fi

kill $PF_PID 2>/dev/null

# 4. Lambda 함수 업데이트
echo ""
echo -e "${GREEN}4. Lambda 함수 설정 업데이트...${NC}"

# VPC 정보 가져오기
VPC_ID="vpc-0bdbee988c0d5e2cc"
SUBNET_IDS="subnet-029b4e47d0be0c4b5,subnet-0c66ca1fea24116a5"
SECURITY_GROUP="sg-0a5b2954e49888013"

# Lambda 환경 변수 업데이트
echo -e "${BLUE}   환경 변수 설정...${NC}"
aws lambda update-function-configuration \
    --function-name kinesis-to-loki \
    --environment Variables={LOKI_ENDPOINT=$LOKI_ENDPOINT} \
    --region ap-northeast-2 \
    --output json > /dev/null

# Lambda VPC 설정
echo -e "${BLUE}   VPC 설정...${NC}"
aws lambda update-function-configuration \
    --function-name kinesis-to-loki \
    --vpc-config SubnetIds=$SUBNET_IDS,SecurityGroupIds=$SECURITY_GROUP \
    --region ap-northeast-2 \
    --output json > /dev/null

# Lambda 함수가 업데이트될 때까지 대기
echo -e "${YELLOW}   Lambda 업데이트 대기 중...${NC}"
for i in {1..12}; do
    STATUS=$(aws lambda get-function-configuration \
        --function-name kinesis-to-loki \
        --region ap-northeast-2 \
        --query 'State' \
        --output text)
    
    if [ "$STATUS" == "Active" ]; then
        echo -e "${GREEN}✅ Lambda 함수 업데이트 완료!${NC}"
        break
    fi
    echo -n "."
    sleep 5
done

# 5. 설정 확인
echo ""
echo -e "${BLUE}=== 설정 완료 ===${NC}"
echo ""
echo -e "${GREEN}Loki 엔드포인트:${NC} $LOKI_ENDPOINT"
echo ""
echo -e "${YELLOW}다음 명령어로 상태를 확인할 수 있습니다:${NC}"
echo "  # NLB 상태 확인"
echo "  kubectl get svc loki-external -n monitoring"
echo ""
echo "  # Lambda 로그 확인"
echo "  aws logs tail /aws/lambda/kinesis-to-loki --follow"
echo ""
echo "  # Loki 로그 확인"
echo "  kubectl logs -n monitoring loki-0 -f"
echo ""
echo -e "${GREEN}Grafana에서 Loki 데이터소스 추가:${NC}"
echo "  URL: $LOKI_ENDPOINT"
echo ""

# 6. Lambda 테스트 실행 옵션
echo -e "${YELLOW}Lambda 테스트를 실행하시겠습니까? (y/n)${NC}"
read -r response
if [[ "$response" =~ ^[Yy]$ ]]; then
    echo -e "${BLUE}Lambda 테스트 실행 중...${NC}"
    
    # 테스트 이벤트 생성
    cat > test-event.json <<EOF
{
  "Records": [
    {
      "kinesis": {
        "data": "$(echo '{"log":"Test log from Lambda","kubernetes":{"namespace":"test","pod_name":"test-pod","container_name":"test-container"}}' | base64)"
      }
    }
  ]
}
EOF
    
    # Lambda 테스트 실행
    aws lambda invoke \
        --function-name kinesis-to-loki \
        --payload file://test-event.json \
        --region ap-northeast-2 \
        response.json
    
    echo -e "${GREEN}테스트 결과:${NC}"
    cat response.json
    rm -f test-event.json response.json
fi

echo ""
echo -e "${GREEN}✅ 모든 설정이 완료되었습니다!${NC}"
