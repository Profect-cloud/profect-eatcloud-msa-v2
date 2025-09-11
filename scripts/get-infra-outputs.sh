#!/bin/bash

# 테라폼 인프라 프로젝트에서 output 값을 추출하여 Helm values에 적용하는 스크립트

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
INFRA_DIR="../profect-eatcloud-msa-v2-infra/infra/envs/dev"

echo "🔍 테라폼 인프라 정보 추출 중..."

# 인프라 디렉토리 존재 확인
if [ ! -d "$INFRA_DIR" ]; then
    echo "❌ 인프라 디렉토리를 찾을 수 없습니다: $INFRA_DIR"
    exit 1
fi

cd "$INFRA_DIR"

# 테라폼 상태 확인
if [ ! -f "terraform.tfstate" ] && [ ! -f ".terraform/terraform.tfstate" ]; then
    echo "⚠️  테라폼 상태 파일이 없습니다. terraform apply를 먼저 실행하세요."
    echo "📋 수동으로 설정해야 할 항목들:"
    echo ""
    echo "1. RDS 엔드포인트들 (서비스별):"
    echo "   - admin-service: eatcloud-dev-admin.xxxxx.ap-northeast-2.rds.amazonaws.com"
    echo "   - customer-service: eatcloud-dev-customer.xxxxx.ap-northeast-2.rds.amazonaws.com"
    echo ""
    echo "2. Kinesis 스트림:"
    echo "   - eatcloud-app-events-dev"
    echo ""
    echo "3. Redis 엔드포인트 (있다면):"
    echo "   - ElastiCache 클러스터 엔드포인트"
    echo ""
    echo "🔧 helm/eatcloud-apps/values.yaml 파일을 직접 수정하세요."
    exit 1
fi

# 테라폼 output 추출
echo "📤 테라폼 output 추출 중..."
TERRAFORM_OUTPUTS=$(terraform output -json 2>/dev/null)

if [ $? -ne 0 ]; then
    echo "❌ 테라폼 output 추출 실패. terraform init이 필요할 수 있습니다."
    exit 1
fi

echo "$TERRAFORM_OUTPUTS" > /tmp/terraform-outputs.json

# 서비스별 DB 엔드포인트 추출
echo "🗄️  데이터베이스 엔드포인트 추출 중..."

# JSON 파일에서 값 추출 (jq 사용)
if command -v jq >/dev/null 2>&1; then
    ADMIN_DB_ENDPOINT=$(echo "$TERRAFORM_OUTPUTS" | jq -r '.service_db_endpoints.value.admin.endpoint // "localhost"')
    CUSTOMER_DB_ENDPOINT=$(echo "$TERRAFORM_OUTPUTS" | jq -r '.service_db_endpoints.value.customer.endpoint // "localhost"')
    KINESIS_STREAM_ARN=$(echo "$TERRAFORM_OUTPUTS" | jq -r '.dev_stream_arn.value // "arn:aws:kinesis:ap-northeast-2:536580887516:stream/eatcloud-app-events-dev"')
else
    echo "⚠️  jq가 설치되지 않았습니다. 수동으로 설정하세요."
    ADMIN_DB_ENDPOINT="localhost"
    CUSTOMER_DB_ENDPOINT="localhost"
    KINESIS_STREAM_ARN="arn:aws:kinesis:ap-northeast-2:536580887516:stream/eatcloud-app-events-dev"
fi

echo "✅ 추출된 정보:"
echo "  - Admin DB: $ADMIN_DB_ENDPOINT"
echo "  - Customer DB: $CUSTOMER_DB_ENDPOINT"
echo "  - Kinesis Stream: $KINESIS_STREAM_ARN"

# Helm values 파일 업데이트
cd "$PROJECT_ROOT"
VALUES_FILE="helm/eatcloud-apps/values.yaml"

if [ -f "$VALUES_FILE" ]; then
    echo "📝 Helm values.yaml 업데이트 중..."
    
    # 백업 생성
    cp "$VALUES_FILE" "${VALUES_FILE}.backup.$(date +%Y%m%d-%H%M%S)"
    
    # RDS 엔드포인트 업데이트 (sed 사용)
    if [ "$ADMIN_DB_ENDPOINT" != "localhost" ]; then
        sed -i.tmp "s/host: eatcloud-dev-admin\.cluster-xyz\.ap-northeast-2\.rds\.amazonaws\.com/host: $ADMIN_DB_ENDPOINT/g" "$VALUES_FILE"
        rm "${VALUES_FILE}.tmp" 2>/dev/null
    fi
    
    if [ "$CUSTOMER_DB_ENDPOINT" != "localhost" ]; then
        sed -i.tmp "s/host: eatcloud-dev-customer\.cluster-xyz\.ap-northeast-2\.rds\.amazonaws\.com/host: $CUSTOMER_DB_ENDPOINT/g" "$VALUES_FILE"
        rm "${VALUES_FILE}.tmp" 2>/dev/null
    fi
    
    echo "✅ Helm values.yaml 업데이트 완료!"
else
    echo "❌ Helm values.yaml 파일을 찾을 수 없습니다: $VALUES_FILE"
fi

echo ""
echo "🚀 다음 단계:"
echo "1. AWS Secrets Manager에서 DB 비밀번호 확인"
echo "2. helm/eatcloud-apps/values.yaml 에서 database.password 값 업데이트"
echo "3. IRSA (IAM Roles for Service Accounts) 설정"
echo "4. helm upgrade로 배포"
