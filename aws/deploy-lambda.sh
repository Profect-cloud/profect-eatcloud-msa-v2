#!/bin/bash

# 최적화된 Lambda 배포 스크립트 - 새 버전을 사용하세요!
echo "❌ 이 스크립트는 더 이상 사용되지 않습니다."
echo "✅ 대신 deploy-lambda-optimized.sh를 사용하세요:"
echo "   chmod +x deploy-lambda-optimized.sh"
echo "   ./deploy-lambda-optimized.sh"
exit 1

echo "3. IAM Role 생성 (Lambda용)..."
# Trust policy 생성
cat > lambda-trust-policy.json << EOF
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

# Lambda execution role 생성
aws iam create-role \
  --role-name lambda-kinesis-to-loki-role \
  --assume-role-policy-document file://lambda-trust-policy.json

# 기본 Lambda 실행 정책 연결
aws iam attach-role-policy \
  --role-name lambda-kinesis-to-loki-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

# Kinesis 읽기 정책 연결
aws iam attach-role-policy \
  --role-name lambda-kinesis-to-loki-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaKinesisExecutionRole

echo "4. Lambda 함수 생성..."
aws lambda create-function \
  --function-name kinesis-to-loki \
  --runtime python3.9 \
  --role arn:aws:iam::536580887516:role/lambda-kinesis-to-loki-role \
  --handler lambda-kinesis-to-loki.lambda_handler \
  --zip-file fileb://lambda-kinesis-to-loki.zip \
  --timeout 30 \
  --memory-size 256 \
  --environment Variables='{LOKI_ENDPOINT=http://k8s-dev-eatcloud-600fc1a967-383401301.ap-northeast-2.elb.amazonaws.com:3100}' \
  --region ap-northeast-2

echo "5. Kinesis Event Source Mapping 생성..."
# Stateless 로그 스트림
aws lambda create-event-source-mapping \
  --function-name kinesis-to-loki \
  --event-source-arn arn:aws:kinesis:ap-northeast-2:536580887516:stream/eatcloud-stateless-logs \
  --starting-position LATEST \
  --batch-size 100 \
  --region ap-northeast-2

# Stateful 로그 스트림
aws lambda create-event-source-mapping \
  --function-name kinesis-to-loki \
  --event-source-arn arn:aws:kinesis:ap-northeast-2:536580887516:stream/eatcloud-stateful-logs \
  --starting-position LATEST \
  --batch-size 100 \
  --region ap-northeast-2

echo "6. Lambda 함수 테스트..."
aws lambda invoke \
  --function-name kinesis-to-loki \
  --payload '{"Records": []}' \
  --region ap-northeast-2 \
  response.json

echo "7. 배포 완료!"
echo "Lambda 함수 상태 확인:"
aws lambda get-function --function-name kinesis-to-loki --region ap-northeast-2

echo ""
echo "CloudWatch 로그 확인:"
echo "aws logs tail /aws/lambda/kinesis-to-loki --follow --region ap-northeast-2"