#!/bin/bash

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== EatCloud 로그 파이프라인 구성 ===${NC}"
echo -e "${YELLOW}(사용자 행동 분석 파이프라인과 독립적으로 구성)${NC}"
echo ""

# 변수 설정
ACCOUNT_ID="536580887516"
REGION="ap-northeast-2"
LOG_STREAM_NAME="eatcloud-logs"  # 기존 스트림 (아마 사용자 행동용)
NEW_LOG_STREAM="eatcloud-system-logs"  # 시스템 로그용 새 스트림
BUCKET_NAME="eatcloud-system-logs-$(date +%Y%m%d)"
FIREHOSE_NAME="system-logs-to-s3"

echo -e "${GREEN}=== 현재 Kinesis Streams 확인 ===${NC}"
aws kinesis list-streams --region $REGION

echo ""
echo -e "${YELLOW}질문: 시스템 로그를 위한 새로운 Kinesis Stream을 생성하시겠습니까?${NC}"
echo -e "${YELLOW}(기존 사용자 행동 분석용 Stream과 분리 권장) (y/n)${NC}"
read -r response

if [[ "$response" =~ ^[Yy]$ ]]; then
    # 1. 새로운 Kinesis Stream 생성 (시스템 로그용)
    echo -e "${GREEN}1. 시스템 로그용 Kinesis Stream 생성...${NC}"
    aws kinesis create-stream \
        --stream-name $NEW_LOG_STREAM \
        --shard-count 2 \
        --region $REGION 2>/dev/null || echo "Stream already exists"
    
    aws kinesis wait stream-exists --stream-name $NEW_LOG_STREAM --region $REGION
    echo -e "${GREEN}   ✅ Stream 생성: $NEW_LOG_STREAM${NC}"
    
    # Fluent Bit 설정 업데이트 필요
    echo ""
    echo -e "${YELLOW}📝 Fluent Bit ConfigMap 업데이트 필요:${NC}"
    echo "   Kinesis output의 stream 이름을 '$NEW_LOG_STREAM'로 변경하세요"
    
    STREAM_TO_USE=$NEW_LOG_STREAM
else
    echo -e "${YELLOW}기존 Stream을 사용합니다: $LOG_STREAM_NAME${NC}"
    STREAM_TO_USE=$LOG_STREAM_NAME
fi

# 2. S3 버킷 생성 (시스템 로그 저장용)
echo ""
echo -e "${GREEN}2. S3 버킷 생성 (시스템 로그용)...${NC}"
aws s3api create-bucket \
    --bucket $BUCKET_NAME \
    --region $REGION \
    --create-bucket-configuration LocationConstraint=$REGION 2>/dev/null || echo "Bucket already exists"

echo -e "${GREEN}   ✅ 버킷 생성: $BUCKET_NAME${NC}"

# 3. Firehose IAM Role
echo -e "${GREEN}3. Firehose IAM Role 생성...${NC}"

cat > firehose-trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "firehose.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

aws iam create-role \
    --role-name firehose-system-logs-role \
    --assume-role-policy-document file://firehose-trust-policy.json \
    --region $REGION 2>/dev/null || echo "Role already exists"

# 4. IAM Policy
cat > firehose-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:AbortMultipartUpload",
        "s3:GetBucketLocation",
        "s3:GetObject",
        "s3:ListBucket",
        "s3:ListBucketMultipartUploads",
        "s3:PutObject"
      ],
      "Resource": [
        "arn:aws:s3:::${BUCKET_NAME}",
        "arn:aws:s3:::${BUCKET_NAME}/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "kinesis:DescribeStream",
        "kinesis:GetShardIterator",
        "kinesis:GetRecords",
        "kinesis:ListShards"
      ],
      "Resource": "arn:aws:kinesis:${REGION}:${ACCOUNT_ID}:stream/${STREAM_TO_USE}"
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "*"
    }
  ]
}
EOF

aws iam put-role-policy \
    --role-name firehose-system-logs-role \
    --policy-name firehose-policy \
    --policy-document file://firehose-policy.json \
    --region $REGION

ROLE_ARN=$(aws iam get-role --role-name firehose-system-logs-role --query 'Role.Arn' --output text)

# 5. Kinesis Data Firehose 생성
echo -e "${GREEN}4. Kinesis Data Firehose 생성...${NC}"

aws firehose create-delivery-stream \
    --delivery-stream-name $FIREHOSE_NAME \
    --delivery-stream-type KinesisStreamAsSource \
    --kinesis-stream-source-configuration "{
        \"KinesisStreamARN\": \"arn:aws:kinesis:${REGION}:${ACCOUNT_ID}:stream/${STREAM_TO_USE}\",
        \"RoleARN\": \"${ROLE_ARN}\"
    }" \
    --extended-s3-destination-configuration "{
        \"RoleARN\": \"${ROLE_ARN}\",
        \"BucketARN\": \"arn:aws:s3:::${BUCKET_NAME}\",
        \"Prefix\": \"logs/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/hour=!{timestamp:HH}/\",
        \"ErrorOutputPrefix\": \"error/\",
        \"BufferingHints\": {
            \"SizeInMBs\": 5,
            \"IntervalInSeconds\": 60
        },
        \"CompressionFormat\": \"GZIP\"
    }" \
    --region $REGION 2>/dev/null || echo "Firehose already exists"

echo -e "${GREEN}   ✅ Firehose 생성: $FIREHOSE_NAME${NC}"

# 6. 정리
rm -f firehose-trust-policy.json firehose-policy.json

echo ""
echo -e "${GREEN}✅ 로그 파이프라인 구성 완료!${NC