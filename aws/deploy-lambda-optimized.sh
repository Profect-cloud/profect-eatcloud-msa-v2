#!/bin/bash

# 최적화된 Lambda 배포 스크립트

set -e

# 변수 설정
FUNCTION_NAME="kinesis-to-loki"
ROLE_NAME="lambda-kinesis-to-loki-role"
REGION="ap-northeast-2"
ACCOUNT_ID="536580887516"
LOKI_ENDPOINT="http://k8s-dev-eatcloud-600fc1a967-383401301.ap-northeast-2.elb.amazonaws.com:3100"

echo "=== Kinesis to Loki Lambda 배포 ===" 

echo "1. 기존 리소스 정리..."
# Event Source Mappings 삭제
aws lambda list-event-source-mappings --function-name $FUNCTION_NAME --region $REGION 2>/dev/null | \
jq -r '.EventSourceMappings[]?.UUID' | \
while read uuid; do
    if [ ! -z "$uuid" ] && [ "$uuid" != "null" ]; then
        echo "Event Source Mapping 삭제: $uuid"
        aws lambda delete-event-source-mapping --uuid "$uuid" --region $REGION
        sleep 5
    fi
done

# Lambda 함수 삭제
if aws lambda get-function --function-name $FUNCTION_NAME --region $REGION 2>/dev/null; then
    echo "기존 Lambda 함수 삭제..."
    aws lambda delete-function --function-name $FUNCTION_NAME --region $REGION
    sleep 10
fi

# IAM Role 정리
if aws iam get-role --role-name $ROLE_NAME 2>/dev/null; then
    echo "기존 IAM 정책 분리..."
    aws iam detach-role-policy --role-name $ROLE_NAME --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole 2>/dev/null || true
    aws iam detach-role-policy --role-name $ROLE_NAME --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaKinesisExecutionRole 2>/dev/null || true
    sleep 5
    echo "기존 IAM Role 삭제..."
    aws iam delete-role --role-name $ROLE_NAME
fi

echo "2. Lambda 함수 패키징..."
rm -rf lambda-package lambda-kinesis-to-loki.zip

# Lambda 함수 코드 생성
cat > lambda-kinesis-to-loki.py << 'EOF'
import json
import base64
import gzip
import requests
import time
from datetime import datetime
import os

def lambda_handler(event, context):
    """
    Kinesis Data Streams에서 Loki로 로그 전송
    """
    loki_endpoint = os.environ['LOKI_ENDPOINT']
    loki_push_url = f"{loki_endpoint}/loki/api/v1/push"
    
    # Loki 스트림 데이터 준비
    streams = {}
    processed_count = 0
    error_count = 0
    
    print(f"Processing {len(event.get('Records', []))} records")
    
    for record in event.get('Records', []):
        try:
            # Kinesis 데이터 디코드
            payload = base64.b64decode(record['kinesis']['data'])
            
            # GZIP 압축 해제 시도
            try:
                payload = gzip.decompress(payload)
            except:
                pass  # 압축되지 않은 데이터
            
            # JSON 파싱
            log_data = json.loads(payload.decode('utf-8'))
            
            # 타임스탬프 생성 (나노초 단위)
            timestamp = int(time.time() * 1000000000)
            
            # 레이블 추출 및 생성
            labels = {
                'job': 'eatcloud-logs',
                'cluster': 'eatcloud',
                'stream': record['eventSourceARN'].split('/')[-1]  # stream name
            }
            
            # 로그 데이터에서 추가 레이블 추출
            if isinstance(log_data, dict):
                # Fluent Bit 로그 형식 처리
                if 'kubernetes' in log_data:
                    k8s_meta = log_data['kubernetes']
                    labels.update({
                        'namespace': k8s_meta.get('namespace_name', 'unknown'),
                        'pod': k8s_meta.get('pod_name', 'unknown'),
                        'container': k8s_meta.get('container_name', 'unknown')
                    })
                
                # 로그 메시지 추출
                log_message = log_data.get('log', log_data.get('message', json.dumps(log_data)))
            else:
                log_message = str(log_data)
            
            # 레이블을 키로 스트림 그룹화
            label_key = json.dumps(labels, sort_keys=True)
            
            if label_key not in streams:
                streams[label_key] = {
                    'stream': labels.copy(),
                    'values': []
                }
            
            streams[label_key]['values'].append([str(timestamp), log_message])
            processed_count += 1
            
        except Exception as e:
            print(f"Record processing error: {str(e)}")
            print(f"Record data: {record}")
            error_count += 1
            continue
    
    # Loki로 전송
    if streams:
        loki_payload = {
            'streams': list(streams.values())
        }
        
        try:
            headers = {
                'Content-Type': 'application/json',
                'X-Scope-OrgID': 'eatcloud'  # 멀티테넌시용 (선택사항)
            }
            
            response = requests.post(
                loki_push_url,
                json=loki_payload,
                headers=headers,
                timeout=30
            )
            
            if response.status_code == 204:
                print(f"Successfully sent {processed_count} logs to Loki")
            else:
                print(f"Loki response error: {response.status_code} - {response.text}")
                raise Exception(f"Loki push failed: {response.status_code}")
                
        except requests.exceptions.RequestException as e:
            print(f"Request error: {str(e)}")
            raise
    
    return {
        'statusCode': 200,
        'body': json.dumps({
            'processed': processed_count,
            'errors': error_count,
            'streams_sent': len(streams)
        })
    }
EOF

# 패키지 디렉토리 생성
mkdir lambda-package
cd lambda-package

# 코드 복사
cp ../lambda-kinesis-to-loki.py .

# requirements.txt 생성
cat > requirements.txt << EOF
requests==2.31.0
urllib3==1.26.18
certifi>=2023.7.22
charset-normalizer>=3.2.0
idna>=3.4
EOF

# 의존성 설치
if command -v python3 &> /dev/null; then
    python3 -m pip install -r requirements.txt -t . --no-deps
elif command -v python &> /dev/null; then
    python -m pip install -r requirements.txt -t . --no-deps
else
    echo "Error: Python not found"
    exit 1
fi

# 패키징
zip -r ../lambda-kinesis-to-loki.zip . -x "*.pyc" "*/__pycache__/*"
cd ..

echo "3. IAM Role 생성..."
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
  --role-name $ROLE_NAME \
  --assume-role-policy-document file://lambda-trust-policy.json

# 필수 정책 연결
aws iam attach-role-policy \
  --role-name $ROLE_NAME \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

aws iam attach-role-policy \
  --role-name $ROLE_NAME \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaKinesisExecutionRole

# IAM 전파 대기
echo "IAM Role 전파 대기..."
sleep 45

echo "4. Lambda 함수 생성..."
aws lambda create-function \
  --function-name $FUNCTION_NAME \
  --runtime python3.9 \
  --role arn:aws:iam::${ACCOUNT_ID}:role/${ROLE_NAME} \
  --handler lambda-kinesis-to-loki.lambda_handler \
  --zip-file fileb://lambda-kinesis-to-loki.zip \
  --timeout 300 \
  --memory-size 512 \
  --environment Variables="{LOKI_ENDPOINT=${LOKI_ENDPOINT}}" \
  --region $REGION \
  --description "Kinesis to Loki log forwarder"

echo "5. Lambda 함수 활성화 대기..."
sleep 15

echo "6. Kinesis Event Source Mapping 생성..."
# Stateless 로그 스트림
STATELESS_MAPPING=$(aws lambda create-event-source-mapping \
  --function-name $FUNCTION_NAME \
  --event-source-arn arn:aws:kinesis:${REGION}:${ACCOUNT_ID}:stream/eatcloud-stateless-logs \
  --starting-position LATEST \
  --batch-size 100 \
  --maximum-batching-window-in-seconds 5 \
  --parallelization-factor 1 \
  --region $REGION \
  --output text --query 'UUID')

echo "Stateless mapping UUID: $STATELESS_MAPPING"

# Stateful 로그 스트림
STATEFUL_MAPPING=$(aws lambda create-event-source-mapping \
  --function-name $FUNCTION_NAME \
  --event-source-arn arn:aws:kinesis:${REGION}:${ACCOUNT_ID}:stream/eatcloud-stateful-logs \
  --starting-position LATEST \
  --batch-size 100 \
  --maximum-batching-window-in-seconds 5 \
  --parallelization-factor 1 \
  --region $REGION \
  --output text --query 'UUID')

echo "Stateful mapping UUID: $STATEFUL_MAPPING"

echo "7. 배포 완료!"
echo ""
echo "=== 배포 정보 ==="
echo "Function Name: $FUNCTION_NAME"
echo "Region: $REGION"
echo "Loki Endpoint: $LOKI_ENDPOINT"
echo ""

echo "=== Lambda 함수 상태 ==="
aws lambda get-function --function-name $FUNCTION_NAME --region $REGION \
  --query 'Configuration.{Name:FunctionName,State:State,Runtime:Runtime,Timeout:Timeout,Memory:MemorySize}'

echo ""
echo "=== Event Source Mappings ==="
aws lambda list-event-source-mappings --function-name $FUNCTION_NAME --region $REGION \
  --query 'EventSourceMappings[].{UUID:UUID,State:State,EventSource:EventSourceArn,BatchSize:BatchSize}'

echo ""
echo "=== 모니터링 명령어 ==="
echo "CloudWatch 로그: aws logs tail /aws/lambda/$FUNCTION_NAME --follow --region $REGION"
echo "Lambda 메트릭: aws cloudwatch get-metric-statistics --namespace AWS/Lambda --metric-name Invocations --dimensions Name=FunctionName,Value=$FUNCTION_NAME --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) --end-time $(date -u +%Y-%m-%dT%H:%M:%S) --period 300 --statistics Sum --region $REGION"

echo ""
echo "정리 중..."
rm -rf lambda-package lambda-trust-policy.json lambda-kinesis-to-loki.py

echo "배포 완료!"
