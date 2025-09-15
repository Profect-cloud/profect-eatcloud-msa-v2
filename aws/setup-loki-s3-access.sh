#!/bin/bash

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Loki S3 Access 설정 ===${NC}"
echo ""

# 변수 설정
ACCOUNT_ID="536580887516"
REGION="ap-northeast-2"
BUCKET_NAME="eatcloud-logs-s3-$(date +%Y%m%d)"
OIDC_ID="17851FDBC517F38FA5FFDABA114B7762"

# 1. Loki용 IAM Role 생성
echo -e "${GREEN}1. Loki IAM Role 생성...${NC}"

# Trust Policy
cat > loki-trust-policy.json <<EOF
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
          "oidc.eks.${REGION}.amazonaws.com/id/${OIDC_ID}:sub": "system:serviceaccount:monitoring:loki",
          "oidc.eks.${REGION}.amazonaws.com/id/${OIDC_ID}:aud": "sts.amazonaws.com"
        }
      }
    }
  ]
}
EOF

# Role 생성
aws iam create-role \
    --role-name loki-s3-access-role \
    --assume-role-policy-document file://loki-trust-policy.json \
    --region $REGION 2>/dev/null || echo "Role already exists"

# 2. S3 Access Policy
echo -e "${GREEN}2. S3 Access Policy 설정...${NC}"

cat > loki-s3-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket",
        "s3:GetBucketLocation"
      ],
      "Resource": "arn:aws:s3:::${BUCKET_NAME}"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::${BUCKET_NAME}/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:ListAllMyBuckets"
      ],
      "Resource": "*"
    }
  ]
}
EOF

# Policy 연결
aws iam put-role-policy \
    --role-name loki-s3-access-role \
    --policy-name loki-s3-policy \
    --policy-document file://loki-s3-policy.json \
    --region $REGION

echo -e "${GREEN}✅ IAM Role 생성 완료${NC}"

# 3. ConfigMap 업데이트 (S3 버킷 이름 수정)
echo -e "${GREEN}3. Loki ConfigMap 업데이트...${NC}"

# 현재 날짜로 버킷 이름 업데이트
kubectl get configmap loki-config -n monitoring -o yaml | \
  sed "s/eatcloud-logs-s3-YYYYMMDD/${BUCKET_NAME}/g" | \
  kubectl apply -f -

# 4. Loki 재시작
echo -e "${GREEN}4. Loki 재시작...${NC}"
kubectl rollout restart statefulset loki -n monitoring

# 5. 정리
rm -f loki-trust-policy.json loki-s3-policy.json

echo ""
echo -e "${GREEN}✅ Loki S3 Access 설정 완료!${NC}"
echo ""
echo -e "${BLUE}=== 확인 명령어 ===${NC}"
echo "# Firehose 상태 확인"
echo "aws firehose describe-delivery-stream --delivery-stream-name eatcloud-logs-to-s3 --region $REGION"
echo ""
echo "# S3 파일 확인 (1-2분 후)"
echo "aws s3 ls s3://$BUCKET_NAME/logs/ --recursive"
echo ""
echo "# Loki 로그 확인"
echo "kubectl logs -n monitoring loki-0 -f"
echo ""
echo "# Grafana에서 Loki 데이터소스 테스트"
echo "kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80"
