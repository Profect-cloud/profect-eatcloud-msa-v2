#!/bin/bash
# EatCloud API 테스트 시나리오
# Kinesis → DocumentDB/Loki 파이프라인 검증

ALB_URL="http://k8s-dev-eatcloud-600fc1a967-383401301.ap-northeast-2.elb.amazonaws.com"
TIMESTAMP=$(date +%s)
TEST_EMAIL="test${TIMESTAMP}@eatcloud.com"
TEST_NAME="Test User ${TIMESTAMP}"

echo "=========================================="
echo "EatCloud API 테스트 시작"
echo "ALB URL: $ALB_URL"
echo "테스트 이메일: $TEST_EMAIL"
echo "=========================================="

# 1. 테스트용 회원가입 (이메일 인증 없이)
echo "1. 테스트 회원가입 중..."
SIGNUP_RESPONSE=$(curl -s -X POST "$ALB_URL/api/v1/auth/register-test" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$TEST_EMAIL\",
    \"password\": \"testpassword123\",
    \"name\": \"$TEST_NAME\",
    \"phoneNumber\": \"010-1234-5678\"
  }")

echo "회원가입 응답: $SIGNUP_RESPONSE"

# 2. 로그인하여 JWT 토큰 획득
echo "2. 로그인 중..."
LOGIN_RESPONSE=$(curl -s -X POST "$ALB_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$TEST_EMAIL\",
    \"password\": \"testpassword123\"
  }")

echo "로그인 응답: $LOGIN_RESPONSE"

# JWT 토큰 추출
JWT_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.data.token // .token // empty')

if [ -z "$JWT_TOKEN" ] || [ "$JWT_TOKEN" == "null" ]; then
    echo "❌ JWT 토큰 획득 실패"
    exit 1
fi

echo "✅ JWT 토큰 획득 성공: ${JWT_TOKEN:0:50}..."

# 3. Passport 토큰 발급
echo "3. Passport 토큰 발급 중..."
PASSPORT_RESPONSE=$(curl -s -X POST "$ALB_URL/api/v1/auth/token/exchange" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json")

echo "Passport 응답: $PASSPORT_RESPONSE"

# Passport 토큰 추출
PASSPORT_TOKEN=$(echo $PASSPORT_RESPONSE | jq -r '.access_token // empty')

if [ -z "$PASSPORT_TOKEN" ] || [ "$PASSPORT_TOKEN" == "null" ]; then
    echo "❌ Passport 토큰 발급 실패"
    exit 1
fi

echo "✅ Passport 토큰 발급 성공: ${PASSPORT_TOKEN:0:50}..."

# 4. 매장 검색 API 호출 (추천 이벤트 → DocumentDB)
echo "4. 매장 검색 API 호출 중..."
STORE_SEARCH_RESPONSE=$(curl -s -X GET "$ALB_URL/api/v1/stores/search?keyword=치킨&page=0&size=10" \
  -H "Authorization: Bearer $PASSPORT_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Session-ID: test-session-${TIMESTAMP}")

echo "매장 검색 응답: $STORE_SEARCH_RESPONSE"

# 5. 매장 목록 조회 (일반적인 API)
echo "5. 매장 목록 조회 중..."
STORES_RESPONSE=$(curl -s -X GET "$ALB_URL/api/v1/stores?page=0&size=5" \
  -H "Authorization: Bearer $PASSPORT_TOKEN" \
  -H "Content-Type: application/json")

echo "매장 목록 응답: $STORES_RESPONSE"

# 6. 매장 클릭 이벤트 로깅 (추천 이벤트 → DocumentDB)
echo "6. 매장 클릭 이벤트 로깅 중..."
STORE_CLICK_RESPONSE=$(curl -s -X POST "$ALB_URL/api/v1/stores/1/click?storeName=테스트매장&category=한식" \
  -H "Authorization: Bearer $PASSPORT_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Session-ID: test-session-${TIMESTAMP}")

echo "매장 클릭 이벤트 응답: $STORE_CLICK_RESPONSE"

# 7. 메뉴 조회 API (상태 유지 로그 → Loki)
echo "7. 메뉴 조회 API 호출 중..."
MENU_RESPONSE=$(curl -s -X GET "$ALB_URL/api/v1/stores/1/menus" \
  -H "Authorization: Bearer $PASSPORT_TOKEN" \
  -H "Content-Type: application/json")

echo "메뉴 조회 응답: $MENU_RESPONSE"

# 8. 주문 관련 API (상태 유지 서비스)
echo "8. 주문 상태 조회 API 호출 중..."
ORDER_STATUS_RESPONSE=$(curl -s -X GET "$ALB_URL/api/v1/orders/status" \
  -H "Authorization: Bearer $PASSPORT_TOKEN" \
  -H "Content-Type: application/json")

echo "주문 상태 응답: $ORDER_STATUS_RESPONSE"

echo "=========================================="
echo "API 테스트 완료"
echo "=========================================="

# 9. Kinesis Analytics 상태 확인
echo "9. Kinesis Analytics 상태 확인 중..."
aws kinesisanalyticsv2 describe-application \
  --application-name eatcloud-dual-stream-processor \
  --region ap-northeast-2 \
  --query 'ApplicationDetail.ApplicationStatus'

# 10. Grafana에서 로그 확인 안내
echo "=========================================="
echo "📊 로그 확인 방법:"
echo "1. Grafana: http://localhost:3000 (kubectl port-forward 필요)"
echo "2. 쿼리: {job=\"eatcloud-logs\"}"
echo "3. DocumentDB 확인: MongoDB Compass 또는 CLI"
echo "=========================================="

echo "테스트 완료! 이제 다음을 확인하세요:"
echo "- Kinesis Analytics 애플리케이션이 Running 상태인지"
echo "- Grafana에서 새로운 로그가 나타나는지"
echo "- DocumentDB에 추천 이벤트가 저장되는지"