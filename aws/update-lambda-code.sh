#!/bin/bash

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Lambda 함수 코드 업데이트 ===${NC}"
echo ""

# 1. 의존성 설치
echo -e "${GREEN}1. 의존성 설치...${NC}"
pip install -r requirements.txt -t package/ --upgrade

# 2. Lambda 함수 코드 복사
echo -e "${GREEN}2. Lambda 함수 패키징...${NC}"
cp lambda_function.py package/
cd package

# 3. ZIP 파일 생성
echo -e "${GREEN}3. ZIP 파일 생성...${NC}"
zip -r ../lambda-kinesis-to-loki.zip . -q
cd ..

# 4. Lambda 함수 업데이트
echo -e "${GREEN}4. Lambda 함수 코드 업데이트...${NC}"
aws lambda update-function-code \
    --function-name kinesis-to-loki \
    --zip-file fileb://lambda-kinesis-to-loki.zip \
    --region ap-northeast-2 \
    --output json > /dev/null

# 5. 업데이트 대기
echo -e "${YELLOW}5. 업데이트 완료 대기 중...${NC}"
for i in {1..12}; do
    STATUS=$(aws lambda get-function-configuration \
        --function-name kinesis-to-loki \
        --region ap-northeast-2 \
        --query 'State' \
        --output text)
    
    if [ "$STATUS" == "Active" ]; then
        echo -e "${GREEN}✅ Lambda 함수 코드 업데이트 완료!${NC}"
        break
    fi
    echo -n "."
    sleep 5
done

# 6. 정리
echo -e "${GREEN}6. 임시 파일 정리...${NC}"
rm -rf package/

echo ""
echo -e "${GREEN}✅ Lambda 함수 업데이트 완료!${NC}"
echo ""
echo -e "${YELLOW}로그 확인:${NC}"
echo "  aws logs tail /aws/lambda/kinesis-to-loki --follow"
