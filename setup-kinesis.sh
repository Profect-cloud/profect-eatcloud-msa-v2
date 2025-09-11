#!/bin/bash

# Phase 3: Kinesis 데이터 파이프라인 구축
echo "[INFO] 🔄 Phase 3: Kinesis 데이터 파이프라인 구축을 시작합니다..."

# 환경 변수 설정
CLUSTER_NAME="eatcloud"
ACCOUNT_ID="536580887516"
REGION="ap-northeast-2"

echo "[INFO] 클러스터: $CLUSTER_NAME"
echo "[INFO] 계정 ID: $ACCOUNT_ID"
echo "[INFO] 리전: $REGION"

# 1. Kinesis Data Streams 생성
echo "[INFO] 1. Kinesis Data Streams 생성 중..."

echo "[INFO] 1.1 eks-logs-buffer 스트림 생성 중..."
aws kinesis create-stream \
  --stream-name eks-logs-buffer \
  --shard-count 1 \
  --region $REGION || echo "스트림이 이미 존재할 수 있습니다."

echo "[INFO] 1.2 eks-processed-logs 스트림 생성 중..."
aws kinesis create-stream \
  --stream-name eks-processed-logs \
  --shard-count 1 \
  --region $REGION || echo "스트림이 이미 존재할 수 있습니다."

echo "[INFO] 1.3 eks-alerts 스트림 생성 중..."
aws kinesis create-stream \
  --stream-name eks-alerts \
  --shard-count 1 \
  --region $REGION || echo "스트림이 이미 존재할 수 있습니다."

echo "[INFO] Kinesis 스트림 상태 확인 중..."
sleep 10

# 스트림 상태 확인
echo "[INFO] 스트림 상태:"
aws kinesis describe-stream --stream-name eks-logs-buffer --region $REGION --query 'StreamDescription.StreamStatus'
aws kinesis describe-stream --stream-name eks-processed-logs --region $REGION --query 'StreamDescription.StreamStatus'
aws kinesis describe-stream --stream-name eks-alerts --region $REGION --query 'StreamDescription.StreamStatus'

# 2. Fluent Bit IAM 역할 생성
echo "[INFO] 2. Fluent Bit IAM 역할 생성 중..."

# OIDC Provider 확인
OIDC_ID=$(aws eks describe-cluster --name $CLUSTER_NAME --region $REGION --query "cluster.identity.oidc.issuer" --output text | cut -d '/' -f 5)
echo "[INFO] OIDC Provider ID: $OIDC_ID"

# Fluent Bit 서비스 계정 역할 생성
cat > fluent-bit-trust-policy.json << EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "Federated": "arn:aws:iam::${ACCOUNT_ID}:oidc-provider/oidc.eks.${REGION}.amazonaws.com/id/${OIDC_ID}"
            },
            "Action": "sts:AssumeRoleWithWebIdentity",
            "Condition": {
                "StringEquals": {
                    "oidc.eks.${REGION}.amazonaws.com/id/${OIDC_ID}:sub": "system:serviceaccount:aws-observability:fluent-bit",
                    "oidc.eks.${REGION}.amazonaws.com/id/${OIDC_ID}:aud": "sts.amazonaws.com"
                }
            }
        }
    ]
}
EOF

echo "[INFO] Fluent Bit IAM 역할 생성 중..."
aws iam create-role \
  --role-name EKSFluentBitRole \
  --assume-role-policy-document file://fluent-bit-trust-policy.json || echo "역할이 이미 존재할 수 있습니다."

# Fluent Bit 정책 생성
cat > fluent-bit-policy.json << EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "kinesis:PutRecord",
                "kinesis:PutRecords",
                "kinesis:DescribeStream"
            ],
            "Resource": [
                "arn:aws:kinesis:${REGION}:${ACCOUNT_ID}:stream/eks-logs-buffer",
                "arn:aws:kinesis:${REGION}:${ACCOUNT_ID}:stream/eks-processed-logs",
                "arn:aws:kinesis:${REGION}:${ACCOUNT_ID}:stream/eks-alerts"
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
            "Resource": "arn:aws:logs:${REGION}:${ACCOUNT_ID}:*"
        }
    ]
}
EOF

echo "[INFO] Fluent Bit 정책 생성 중..."
aws iam create-policy \
  --policy-name EKSFluentBitPolicy \
  --policy-document file://fluent-bit-policy.json || echo "정책이 이미 존재할 수 있습니다."

echo "[INFO] 정책을 역할에 연결 중..."
aws iam attach-role-policy \
  --role-name EKSFluentBitRole \
  --policy-arn arn:aws:iam::${ACCOUNT_ID}:policy/EKSFluentBitPolicy || echo "정책이 이미 연결되어 있을 수 있습니다."

# 3. aws-observability 네임스페이스에 ConfigMap 생성
echo "[INFO] 3. Fluent Bit ConfigMap 생성 중..."

cat > aws-logging-configmap.yaml << EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: aws-logging
  namespace: aws-observability
data:
  output.conf: |
    [OUTPUT]
        Name kinesis_streams
        Match *
        region ${REGION}
        stream eks-logs-buffer
        time_key @timestamp
        time_key_format %Y-%m-%dT%H:%M:%S.%L%z
        
  parsers.conf: |
    [PARSER]
        Name docker
        Format json
        Time_Key time
        Time_Format %Y-%m-%dT%H:%M:%S.%L
        Time_Keep On
        
  filters.conf: |
    [FILTER]
        Name parser
        Match *
        Key_Name log
        Parser docker
        Reserve_Data true
        Preserve_Key true
EOF

echo "[INFO] ConfigMap 적용 중..."
kubectl apply -f aws-logging-configmap.yaml

# 4. Fluent Bit 서비스 계정 생성
echo "[INFO] 4. Fluent Bit 서비스 계정 생성 중..."

cat > fluent-bit-serviceaccount.yaml << EOF
apiVersion: v1
kind: ServiceAccount
metadata:
  name: fluent-bit
  namespace: aws-observability
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::${ACCOUNT_ID}:role/EKSFluentBitRole
EOF

kubectl apply -f fluent-bit-serviceaccount.yaml

echo "[SUCCESS] ✅ Phase 3: Kinesis 데이터 파이프라인 구축 완료!"
echo "[INFO] 📊 생성된 리소스:"
echo "  - Kinesis Streams: eks-logs-buffer, eks-processed-logs, eks-alerts"
echo "  - IAM Role: EKSFluentBitRole"
echo "  - ConfigMap: aws-logging (aws-observability 네임스페이스)"
echo "  - ServiceAccount: fluent-bit (aws-observability 네임스페이스)"

echo "[INFO] 🔍 Kinesis 스트림 최종 상태 확인:"
aws kinesis list-streams --region $REGION

# 임시 파일 정리
rm -f fluent-bit-trust-policy.json fluent-bit-policy.json

echo "[INFO] 🎯 다음 단계: Phase 4 - Kinesis Analytics 설정"
