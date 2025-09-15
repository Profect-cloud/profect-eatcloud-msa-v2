#!/bin/bash

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Kinesis Data Streams → Firehose → S3 설정 ===${NC}"
echo ""

# 변수 설정
ACCOUNT_ID="536580887516"
REGION="ap-northeast-2"
STREAM_NAME="eatcloud-logs"
BUCKET_NAME="eatcloud-logs-s3-$(date +%Y%m%d)"
FIREHOSE_NAME="eatcloud-logs-to-s3"

# 1. S3 버킷 생성
echo -e "${GREEN}1. S3 버킷 생성...${NC}"
aws s3api create-bucket \
    --bucket $BUCKET_NAME \
    --region $REGION \
    --create-bucket-configuration LocationConstraint=$REGION 2>/dev/null || echo "Bucket already exists or error"

echo -e "${GREEN}   버킷: $BUCKET_NAME${NC}"

# 2. Firehose용 IAM Role 생성
echo -e "${GREEN}2. Firehose IAM Role 생성...${NC}"

# Trust Policy
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

# Role 생성
aws iam create-role \
    --role-name firehose-to-s3-role \
    --assume-role-policy-document file://firehose-trust-policy.json \
    --region $REGION 2>/dev/null || echo "Role already exists"

# 3. IAM Policy 생성 및 연결
echo -e "${GREEN}3. IAM Policy 설정...${NC}"

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
        "kinesis:ListShards",
        "kinesis:SubscribeToShard",
        "kinesis:DescribeStreamSummary",
        "kinesis:ListStreams"
      ],
      "Resource": "arn:aws:kinesis:${REGION}:${ACCOUNT_ID}:stream/${STREAM_NAME}"
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

# Policy 연결
aws iam put-role-policy \
    --role-name firehose-to-s3-role \
    --policy-name firehose-s3-policy \
    --policy-document file://firehose-policy.json \
    --region $REGION

# Role ARN 가져오기
ROLE_ARN=$(aws iam get-role --role-name firehose-to-s3-role --query 'Role.Arn' --output text)
echo -e "${GREEN}   Role ARN: $ROLE_ARN${NC}"

# 4. Kinesis Data Firehose 생성
echo -e "${GREEN}4. Kinesis Data Firehose 생성...${NC}"

aws firehose create-delivery-stream \
    --delivery-stream-name $FIREHOSE_NAME \
    --delivery-stream-type KinesisStreamAsSource \
    --kinesis-stream-source-configuration "{
        \"KinesisStreamARN\": \"arn:aws:kinesis:${REGION}:${ACCOUNT_ID}:stream/${STREAM_NAME}\",
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
        \"CompressionFormat\": \"GZIP\",
        \"CloudWatchLoggingOptions\": {
            \"Enabled\": true,
            \"LogGroupName\": \"/aws/kinesisfirehose/${FIREHOSE_NAME}\",
            \"LogStreamName\": \"S3Delivery\"
        }
    }" \
    --region $REGION 2>/dev/null || echo "Firehose already exists or error"

# 5. 상태 확인
echo -e "${YELLOW}5. Firehose 상태 확인...${NC}"
STATUS=$(aws firehose describe-delivery-stream \
    --delivery-stream-name $FIREHOSE_NAME \
    --region $REGION \
    --query 'DeliveryStreamDescription.DeliveryStreamStatus' \
    --output text)

echo -e "${GREEN}   상태: $STATUS${NC}"

# 6. 정리
rm -f firehose-trust-policy.json firehose-policy.json

echo ""
echo -e "${GREEN}✅ Kinesis Firehose 설정 완료!${NC}"
echo ""
echo -e "${BLUE}=== 설정 정보 ===${NC}"
echo -e "S3 버킷: ${YELLOW}$BUCKET_NAME${NC}"
echo -e "Firehose: ${YELLOW}$FIREHOSE_NAME${NC}"
echo -e "리전: ${YELLOW}$REGION${NC}"
echo ""
echo -e "${BLUE}로그는 다음 경로에 저장됩니다:${NC}"
echo -e "s3://$BUCKET_NAME/logs/year=YYYY/month=MM/day=DD/hour=HH/"
echo ""
echo -e "${YELLOW}참고: 첫 로그 파일이 S3에 나타나기까지 1-2분 소요됩니다.${NC}"
