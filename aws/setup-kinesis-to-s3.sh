#!/bin/bash

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Kinesis to S3 설정 ===${NC}"
echo ""

# 1. S3 버킷 생성
echo -e "${GREEN}1. S3 버킷 생성...${NC}"
BUCKET_NAME="eatcloud-logs-$(date +%Y%m%d)"
aws s3 mb s3://$BUCKET_NAME --region ap-northeast-2 2>/dev/null || echo "Bucket already exists"

# 2. IAM Role for Firehose
echo -e "${GREEN}2. Firehose IAM Role 생성...${NC}"
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
    --role-name kinesis-firehose-s3-role \
    --assume-role-policy-document file://firehose-trust-policy.json \
    --region ap-northeast-2 2>/dev/null || echo "Role already exists"

# 3. IAM Policy
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
      "Resource": "arn:aws:kinesis:ap-northeast-2:536580887516:stream/eatcloud-logs"
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
    --role-name kinesis-firehose-s3-role \
    --policy-name firehose-s3-policy \
    --policy-document file://firehose-policy.json \
    --region ap-northeast-2

# 4. Kinesis Data Firehose 생성
echo -e "${GREEN}3. Kinesis Data Firehose 생성...${NC}"
ROLE_ARN=$(aws iam get-role --role-name kinesis-firehose-s3-role --query 'Role.Arn' --output text)

aws firehose create-delivery-stream \
    --delivery-stream-name eatcloud-logs-to-s3 \
    --delivery-stream-type KinesisStreamAsSource \
    --kinesis-stream-source-configuration "KinesisStreamARN=arn:aws:kinesis:ap-northeast-2:536580887516:stream/eatcloud-logs,RoleARN=$ROLE_ARN" \
    --extended-s3-destination-configuration "{
        \"RoleARN\": \"$ROLE_ARN\",
        \"BucketARN\": \"arn:aws:s3:::$BUCKET_NAME\",
        \"Prefix\": \"logs/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/\",
        \"ErrorOutputPrefix\": \"error/\",
        \"BufferingHints\": {
            \"SizeInMBs\": 5,
            \"IntervalInSeconds\": 60
        },
        \"CompressionFormat\": \"GZIP\",
        \"CloudWatchLoggingOptions\": {
            \"Enabled\": true,
            \"LogGroupName\": \"/aws/kinesisfirehose/eatcloud-logs\",
            \"LogStreamName\": \"S3Delivery\"
        },
        \"ProcessingConfiguration\": {
            \"Enabled\": true,
            \"Processors\": [{
                \"Type\": \"Lambda\",
                \"Parameters\": [{
                    \"ParameterName\": \"LambdaArn\",
                    \"ParameterValue\": \"arn:aws:lambda:ap-northeast-2:536580887516:function:transform-logs-for-loki\"
                }]
            }]
        },
        \"DataFormatConversionConfiguration\": {
            \"Enabled\": false
        }
    }" \
    --region ap-northeast-2

echo -e "${GREEN}✅ Kinesis to S3 설정 완료!${NC}"
echo ""
echo -e "${YELLOW}S3 버킷:${NC} $BUCKET_NAME"
echo -e "${YELLOW}Firehose 스트림:${NC} eatcloud-logs-to-s3"
echo ""
echo -e "${BLUE}다음 단계:${NC}"
echo "1. Loki를 S3 backend로 설정"
echo "2. 또는 S3에서 Loki로 주기적으로 import하는 Lambda 생성"

# 정리
rm -f firehose-trust-policy.json firehose-policy.json
