#!/bin/bash

# Phase 3 수정: CloudWatch Logs 기반 모니터링 파이프라인
echo "[INFO] 🔄 Phase 3: CloudWatch Logs 기반 모니터링 파이프라인 구축을 시작합니다..."

# 환경 변수 설정
CLUSTER_NAME="eatcloud"
ACCOUNT_ID="536580887516"
REGION="ap-northeast-2"

echo "[INFO] 클러스터: $CLUSTER_NAME"
echo "[INFO] 계정 ID: $ACCOUNT_ID"
echo "[INFO] 리전: $REGION"

# 1. CloudWatch 로그 그룹 생성
echo "[INFO] 1. CloudWatch 로그 그룹 생성 중..."

echo "[INFO] 1.1 EKS 클러스터 로그 그룹 생성 중..."
aws logs create-log-group \
  --log-group-name /aws/eks/${CLUSTER_NAME}/cluster \
  --region $REGION || echo "로그 그룹이 이미 존재할 수 있습니다."

echo "[INFO] 1.2 애플리케이션 로그 그룹 생성 중..."
aws logs create-log-group \
  --log-group-name /aws/eks/${CLUSTER_NAME}/application \
  --region $REGION || echo "로그 그룹이 이미 존재할 수 있습니다."

echo "[INFO] 1.3 Fargate 로그 그룹 생성 중..."
aws logs create-log-group \
  --log-group-name /aws/eks/${CLUSTER_NAME}/fargate \
  --region $REGION || echo "로그 그룹이 이미 존재할 수 있습니다."

# 2. Fluent Bit 설정 (CloudWatch 출력으로 수정)
echo "[INFO] 2. Fluent Bit CloudWatch 설정 업데이트 중..."

cat > aws-logging-configmap-cloudwatch.yaml << EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: aws-logging
  namespace: aws-observability
data:
  output.conf: |
    [OUTPUT]
        Name cloudwatch_logs
        Match *
        region ${REGION}
        log_group_name /aws/eks/${CLUSTER_NAME}/fargate
        log_stream_prefix fargate-
        auto_create_group true
        
  parsers.conf: |
    [PARSER]
        Name docker
        Format json
        Time_Key time
        Time_Format %Y-%m-%dT%H:%M:%S.%L
        Time_Keep On
        
    [PARSER]
        Name cri
        Format regex
        Regex ^(?<time>[^ ]+) (?<stream>stdout|stderr) (?<logtag>[^ ]*) (?<message>.*)$
        Time_Key time
        