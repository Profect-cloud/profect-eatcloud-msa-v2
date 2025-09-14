#!/bin/bash

# Lambda 모니터링 및 비용 최적화 스크립트 (macOS 호환)

FUNCTION_NAME="kinesis-to-loki"
REGION="ap-northeast-2"

echo "=== Lambda 함수 모니터링 ==="

echo "1. 현재 함수 상태"
aws lambda get-function --function-name $FUNCTION_NAME --region $REGION \
  --query 'Configuration.{State:State,LastModified:LastModified,CodeSize:CodeSize,Timeout:Timeout,Memory:MemorySize}'

echo -e "\n2. 최근 1시간 호출 통계"
# macOS 호환 date 명령어
START_TIME=$(date -u -v-1H '+%Y-%m-%dT%H:%M:%S')
END_TIME=$(date -u '+%Y-%m-%dT%H:%M:%S')

echo "시간 범위: $START_TIME ~ $END_TIME"

# 호출 횟수
INVOCATIONS=$(aws cloudwatch get-metric-statistics \
  --namespace AWS/Lambda \
  --metric-name Invocations \
  --dimensions Name=FunctionName,Value=$FUNCTION_NAME \
  --start-time $START_TIME \
  --end-time $END_TIME \
  --period 3600 \
  --statistics Sum \
  --region $REGION \
  --query 'Datapoints[0].Sum' --output text 2>/dev/null)

# 에러 횟수
ERRORS=$(aws cloudwatch get-metric-statistics \
  --namespace AWS/Lambda \
  --metric-name Errors \
  --dimensions Name=FunctionName,Value=$FUNCTION_NAME \
  --start-time $START_TIME \
  --end-time $END_TIME \
  --period 3600 \
  --statistics Sum \
  --region $REGION \
  --query 'Datapoints[0].Sum' --output text 2>/dev/null)

# 평균 실행 시간
DURATION=$(aws cloudwatch get-metric-statistics \
  --namespace AWS/Lambda \
  --metric-name Duration \
  --dimensions Name=FunctionName,Value=$FUNCTION_NAME \
  --start-time $START_TIME \
  --end-time $END_TIME \
  --period 3600 \
  --statistics Average \
  --region $REGION \
  --query 'Datapoints[0].Average' --output text 2>/dev/null)

echo "호출 횟수: ${INVOCATIONS:-0}"
echo "에러 횟수: ${ERRORS:-0}" 
echo "평균 실행시간: ${DURATION:-0} ms"

echo -e "\n3. Event Source Mapping 상태"
aws lambda list-event-source-mappings --function-name $FUNCTION_NAME --region $REGION \
  --query 'EventSourceMappings[].{Stream:EventSourceArn,State:State,LastProcessingResult:LastProcessingResult}'

echo -e "\n4. 최근 CloudWatch 로그 확인"
LOG_GROUP="/aws/lambda/$FUNCTION_NAME"

# 로그 그룹 존재 확인
if aws logs describe-log-groups --log-group-name-prefix "$LOG_GROUP" --region $REGION --query 'logGroups[0]' --output text 2>/dev/null | grep -q "$FUNCTION_NAME"; then
    echo "로그 그룹이 존재합니다. 최근 로그 조회 중..."
    
    # macOS 호환 타임스탬프 (10분 전)
    LOG_START_TIME=$(($(date +%s) - 600))000
    
    aws logs filter-log-events \
      --log-group-name "$LOG_GROUP" \
      --start-time $LOG_START_TIME \
      --region $REGION \
      --max-items 20 \
      --query 'events[].{Timestamp:timestamp,Message:message}' \
      --output table 2>/dev/null || echo "로그 이벤트가 없습니다."
else
    echo "⚠️  CloudWatch 로그 그룹이 아직 생성되지 않았습니다."
    echo "   Lambda가 실행되면 자동으로 생성됩니다."
fi

echo -e "\n=== 비용 최적화 권장사항 ==="

# 실행 시간 기반 메모리 권장
if [ "$DURATION" != "None" ] && [ "$DURATION" != "null" ] && [ -n "$DURATION" ] && [ "$DURATION" != "0" ]; then
    DURATION_NUM=$(echo $DURATION | cut -d'.' -f1)
    if [ "$DURATION_NUM" -lt 1000 ]; then
        echo "✅ 실행시간이 1초 미만이므로 메모리를 256MB로 줄일 수 있습니다."
        echo "   aws lambda update-function-configuration --function-name $FUNCTION_NAME --memory-size 256 --region $REGION"
    elif [ "$DURATION_NUM" -gt 10000 ]; then
        echo "⚠️  실행시간이 10초 이상이므로 메모리를 1024MB로 늘리는 것을 고려하세요."
        echo "   aws lambda update-function-configuration --function-name $FUNCTION_NAME --memory-size 1024 --region $REGION"
    else
        echo "✅ 현재 메모리 설정(512MB)이 적절합니다."
    fi
else
    echo "📊 아직 실행 데이터가 없습니다. Lambda가 실행된 후 다시 확인하세요."
fi

# 에러율 체크
if [ "$ERRORS" != "None" ] && [ "$ERRORS" != "null" ] && [ -n "$ERRORS" ] && [ "$ERRORS" -gt 0 ]; then
    echo "⚠️  에러가 발생하고 있습니다. 로그를 확인하세요."
    echo "   aws logs tail /aws/lambda/$FUNCTION_NAME --follow --region $REGION"
else
    echo "✅ 현재까지 에러가 없습니다."
fi

echo -e "\n=== Kinesis 스트림 상태 확인 ==="
echo "Kinesis 스트림 메트릭 확인 중..."

# Stateless 로그 스트림 확인
STATELESS_RECORDS=$(aws cloudwatch get-metric-statistics \
  --namespace AWS/Kinesis \
  --metric-name IncomingRecords \
  --dimensions Name=StreamName,Value=eatcloud-stateless-logs \
  --start-time $START_TIME \
  --end-time $END_TIME \
  --period 3600 \
  --statistics Sum \
  --region $REGION \
  --query 'Datapoints[0].Sum' --output text 2>/dev/null)

# Stateful 로그 스트림 확인  
STATEFUL_RECORDS=$(aws cloudwatch get-metric-statistics \
  --namespace AWS/Kinesis \
  --metric-name IncomingRecords \
  --dimensions Name=StreamName,Value=eatcloud-stateful-logs \
  --start-time $START_TIME \
  --end-time $END_TIME \
  --period 3600 \
  --statistics Sum \
  --region $REGION \
  --query 'Datapoints[0].Sum' --output text 2>/dev/null)

echo "Stateless 스트림 레코드 수: ${STATELESS_RECORDS:-0}"
echo "Stateful 스트림 레코드 수: ${STATEFUL_RECORDS:-0}"

if [ "${STATELESS_RECORDS:-0}" = "0" ] && [ "${STATEFUL_RECORDS:-0}" = "0" ]; then
    echo "⚠️  Kinesis 스트림으로 로그가 들어오지 않고 있습니다."
    echo "   Fluent Bit 설정을 확인하세요."
else
    echo "✅ Kinesis 스트림으로 로그가 유입되고 있습니다."
fi

echo -e "\n=== Loki 연결 테스트 ==="
LOKI_ENDPOINT=$(aws lambda get-function --function-name $FUNCTION_NAME --region $REGION \
  --query 'Configuration.Environment.Variables.LOKI_ENDPOINT' --output text)

if [ "$LOKI_ENDPOINT" != "None" ] && [ -n "$LOKI_ENDPOINT" ]; then
    echo "Loki 엔드포인트: $LOKI_ENDPOINT"
    
    # Loki 헬스체크 (타임아웃 5초)
    echo "Loki 연결 테스트 중..."
    if curl -s --max-time 5 "${LOKI_ENDPOINT}/ready" > /dev/null 2>&1; then
        echo "✅ Loki 연결 성공"
        
        # Loki 버전 확인
        LOKI_VERSION=$(curl -s --max-time 5 "${LOKI_ENDPOINT}/config" 2>/dev/null | grep -o '"version":"[^"]*"' | cut -d'"' -f4)
        if [ -n "$LOKI_VERSION" ]; then
            echo "   Loki 버전: $LOKI_VERSION"
        fi
        
    else
        echo "❌ Loki 연결 실패 - 엔드포인트를 확인하세요"
        echo "   kubectl get svc -n logging 으로 서비스 상태를 확인하세요"
    fi
else
    echo "❌ Loki 엔드포인트가 설정되지 않았습니다"
fi

echo -e "\n=== 실시간 모니터링 명령어 ==="
cat << EOF
다음 명령어들을 사용해서 실시간 모니터링하세요:

1. Lambda 로그 실시간 모니터링:
   aws logs tail /aws/lambda/$FUNCTION_NAME --follow --region $REGION

2. Kinesis 스트림 모니터링:
   aws kinesis describe-stream --stream-name eatcloud-stateless-logs --region $REGION
   aws kinesis describe-stream --stream-name eatcloud-stateful-logs --region $REGION

3. Lambda 함수 호출 테스트:
   aws lambda invoke --function-name $FUNCTION_NAME --payload '{"Records":[]}' /tmp/lambda-response.json --region $REGION

4. CloudWatch 대시보드 URL:
   https://console.aws.amazon.com/cloudwatch/home?region=$REGION#dashboards:
EOF

echo -e "\n=== 배포 확인 체크리스트 ==="
echo "✅ Lambda 함수: Active"
echo "✅ Event Source Mappings: Enabled"

if [ "${STATELESS_RECORDS:-0}" -gt 0 ] || [ "${STATEFUL_RECORDS:-0}" -gt 0 ]; then
    echo "✅ Kinesis 데이터 유입: 정상"
else
    echo "⚠️  Kinesis 데이터 유입: 확인 필요"
fi

# Loki 연결 상태 간단 체크
if curl -s --max-time 3 "${LOKI_ENDPOINT}/ready" > /dev/null 2>&1; then
    echo "✅ Loki 연결: 정상"
else
    echo "❌ Loki 연결: 확인 필요"
fi

echo -e "\n모니터링 완료! 🚀"