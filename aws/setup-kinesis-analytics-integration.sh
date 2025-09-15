#!/bin/bash

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Kinesis Analytics 통합 설정 ===${NC}"
echo ""

# 변수 설정
ACCOUNT_ID="536580887516"
REGION="ap-northeast-2"
INPUT_STREAM="eatcloud-logs"
OUTPUT_STREAM="eatcloud-logs-processed"
ANALYTICS_APP="eatcloud-log-processor"
BUCKET_NAME="eatcloud-logs-s3-$(date +%Y%m%d)"

# 1. 출력용 Kinesis Stream 생성
echo -e "${GREEN}1. 처리된 로그용 Kinesis Stream 생성...${NC}"
aws kinesis create-stream \
    --stream-name $OUTPUT_STREAM \
    --shard-count 2 \
    --region $REGION 2>/dev/null || echo "Stream already exists"

# Stream 활성화 대기
echo -e "${YELLOW}   Stream 활성화 대기 중...${NC}"
aws kinesis wait stream-exists --stream-name $OUTPUT_STREAM --region $REGION

# 2. Kinesis Analytics 애플리케이션 생성
echo -e "${GREEN}2. Kinesis Analytics 애플리케이션 생성...${NC}"

# IAM Role for Analytics
cat > analytics-trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "kinesisanalytics.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

aws iam create-role \
    --role-name kinesis-analytics-role \
    --assume-role-policy-document file://analytics-trust-policy.json \
    --region $REGION 2>/dev/null || echo "Role already exists"

# Analytics Policy
cat > analytics-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "kinesis:DescribeStream",
        "kinesis:GetShardIterator",
        "kinesis:GetRecords",
        "kinesis:ListShards",
        "kinesis:PutRecord",
        "kinesis:PutRecords"
      ],
      "Resource": [
        "arn:aws:kinesis:${REGION}:${ACCOUNT_ID}:stream/${INPUT_STREAM}",
        "arn:aws:kinesis:${REGION}:${ACCOUNT_ID}:stream/${OUTPUT_STREAM}"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogGroups",
        "logs:DescribeLogStreams"
      ],
      "Resource": "*"
    }
  ]
}
EOF

aws iam put-role-policy \
    --role-name kinesis-analytics-role \
    --policy-name analytics-policy \
    --policy-document file://analytics-policy.json \
    --region $REGION

ROLE_ARN=$(aws iam get-role --role-name kinesis-analytics-role --query 'Role.Arn' --output text)

# 3. SQL 애플리케이션 코드
cat > analytics-sql.sql <<'EOF'
-- 입력 스트림 생성
CREATE OR REPLACE STREAM "TEMP_STREAM" (
    log VARCHAR(8192),
    kubernetes_namespace VARCHAR(256),
    kubernetes_pod_name VARCHAR(256),
    kubernetes_container_name VARCHAR(256),
    log_level VARCHAR(50),
    timestamp_ms BIGINT
);

-- 로그 파싱 및 필터링
CREATE OR REPLACE PUMP "STREAM_PUMP" AS 
INSERT INTO "TEMP_STREAM"
SELECT STREAM
    "log",
    "kubernetes"."namespace_name" as kubernetes_namespace,
    "kubernetes"."pod_name" as kubernetes_pod_name,
    "kubernetes"."container_name" as kubernetes_container_name,
    CASE 
        WHEN "log" LIKE '%ERROR%' THEN 'ERROR'
        WHEN "log" LIKE '%WARN%' THEN 'WARN'
        WHEN "log" LIKE '%INFO%' THEN 'INFO'
        ELSE 'DEBUG'
    END as log_level,
    ROWTIME as timestamp_ms
FROM "SOURCE_SQL_STREAM_001"
WHERE "log" IS NOT NULL
    AND LENGTH("log") > 0;

-- 에러 로그 집계 (5분 윈도우)
CREATE OR REPLACE STREAM "ERROR_STATS_STREAM" (
    namespace VARCHAR(256),
    error_count INTEGER,
    window_start TIMESTAMP,
    window_end TIMESTAMP
);

CREATE OR REPLACE PUMP "ERROR_STATS_PUMP" AS 
INSERT INTO "ERROR_STATS_STREAM"
SELECT STREAM
    kubernetes_namespace,
    COUNT(*) as error_count,
    ROWTIME - INTERVAL '5' MINUTE as window_start,
    ROWTIME as window_end
FROM "TEMP_STREAM"
WHERE log_level = 'ERROR'
GROUP BY kubernetes_namespace, ROWTIME RANGE INTERVAL '5' MINUTE;

-- 출력 스트림
CREATE OR REPLACE STREAM "DESTINATION_SQL_STREAM" (
    log VARCHAR(8192),
    kubernetes OBJECT,
    log_level VARCHAR(50),
    processed_at TIMESTAMP
);

CREATE OR REPLACE PUMP "OUTPUT_PUMP" AS 
INSERT INTO "DESTINATION_SQL_STREAM"
SELECT STREAM
    "log",
    CURSOR(
        SELECT STREAM 
            kubernetes_namespace as "namespace",
            kubernetes_pod_name as "pod_name",
            kubernetes_container_name as "container_name"
        FROM "TEMP_STREAM"
    ) as kubernetes,
    log_level,
    CURRENT_TIMESTAMP as processed_at
FROM "TEMP_STREAM";
EOF

echo -e "${GREEN}   SQL 애플리케이션 코드 생성 완료${NC}"

# 4. Firehose 생성 (처리된 데이터를 S3로)
echo -e "${GREEN}3. Kinesis Firehose 생성 (처리된 데이터 → S3)...${NC}"

# S3 버킷 생성
aws s3api create-bucket \
    --bucket $BUCKET_NAME \
    --region $REGION \
    --create-bucket-configuration LocationConstraint=$REGION 2>/dev/null || echo "Bucket exists"

# Firehose Role
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
    --role-name firehose-processed-logs-role \
    --assume-role-policy-document file://firehose-trust-policy.json \
    --region $REGION 2>/dev/null || echo "Role exists"

# Firehose Policy
cat > firehose-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:*"
      ],
      "Resource": [
        "arn:aws:s3:::${BUCKET_NAME}",
        "arn:aws:s3:::${BUCKET_NAME}/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "kinesis:*"
      ],
      "Resource": "arn:aws:kinesis:${REGION}:${ACCOUNT_ID}:stream/${OUTPUT_STREAM}"
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:*"
      ],
      "Resource": "*"
    }
  ]
}
EOF

aws iam put-role-policy \
    --role-name firehose-processed-logs-role \
    --policy-name firehose-policy \
    --policy-document file://firehose-policy.json \
    --region $REGION

FIREHOSE_ROLE_ARN=$(aws iam get-role --role-name firehose-processed-logs-role --query 'Role.Arn' --output text)

# Firehose 생성
aws firehose create-delivery-stream \
    --delivery-stream-name "${OUTPUT_STREAM}-to-s3" \
    --delivery-stream-type KinesisStreamAsSource \
    --kinesis-stream-source-configuration "{
        \"KinesisStreamARN\": \"arn:aws:kinesis:${REGION}:${ACCOUNT_ID}:stream/${OUTPUT_STREAM}\",
        \"RoleARN\": \"${FIREHOSE_ROLE_ARN}\"
    }" \
    --extended-s3-destination-configuration "{
        \"RoleARN\": \"${FIREHOSE_ROLE_ARN}\",
        \"BucketARN\": \"arn:aws:s3:::${BUCKET_NAME}\",
        \"Prefix\": \"processed-logs/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/hour=!{timestamp:HH}/\",
        \"ErrorOutputPrefix\": \"error/\",
        \"BufferingHints\": {
            \"SizeInMBs\": 5,
            \"IntervalInSeconds\": 60
        },
        \"CompressionFormat\": \"GZIP\",
        \"CloudWatchLoggingOptions\": {
            \"Enabled\": true,
            \"LogGroupName\": \"/aws/kinesisfirehose/${OUTPUT_STREAM}-to-s3\",
            \"LogStreamName\": \"S3Delivery\"
        },
        \"DataFormatConversionConfiguration\": {
            \"Enabled\": true,
            \"OutputFormatConfiguration\": {
                \"Serializer\": {
                    \"ParquetSerDe\": {}
                }
            },
            \"SchemaConfiguration\": {
                \"DatabaseName\": \"default\",
                \"TableName\": \"eatcloud_logs\",
                \"RoleARN\": \"${FIREHOSE_ROLE_ARN}\"
            }
        }
    }" \
    --region $REGION 2>/dev/null || echo "Firehose exists"

# 5. 정리
rm -f analytics-trust-policy.json analytics-policy.json
rm -f firehose-trust-policy.json firehose-policy.json

echo ""
echo -e "${GREEN}✅ Kinesis Analytics 통합 설정 완료!${NC}"
echo ""
echo -e "${BLUE}=== 구성 정보 ===${NC}"
echo -e "입력 Stream: ${YELLOW}${INPUT_STREAM}${NC}"
echo -e "Analytics App: ${YELLOW}${ANALYTICS_APP}${NC}"
echo -e "출력 Stream: ${YELLOW}${OUTPUT_STREAM}${NC}"
echo -e "S3 버킷: ${YELLOW}${BUCKET_NAME}${NC}"
echo ""
echo -e "${BLUE}=== 데이터 흐름 ===${NC}"
echo "1. EKS Logs → Kinesis Streams (${INPUT_STREAM})"
echo "2. → Kinesis Analytics (실시간 처리)"
echo "3. → Kinesis Streams (${OUTPUT_STREAM})"
echo "4. → Kinesis Firehose"
echo "5. → S3 (${BUCKET_NAME})"
echo "6. → Loki (S3 backend)"
echo ""
echo -e "${YELLOW}참고: Kinesis Analytics 애플리케이션은 AWS Console에서 생성/수정하세요${NC}"
echo "https://console.aws.amazon.com/kinesisanalytics/"
