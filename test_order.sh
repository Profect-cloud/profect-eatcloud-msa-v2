#!/bin/bash

# EatCloud MSA 주문 테스트 스크립트
# 사용자 등록부터 주문 생성까지 전체 플로우를 자동화

set -e  # 에러 발생 시 스크립트 중단

# 설정 변수
BASE_URL="http://k8s-dev-eatcloud-600fc1a967-383401301.ap-northeast-2.elb.amazonaws.com"
TIMESTAMP=$(date +%s)
TEST_EMAIL="test${TIMESTAMP}@example.com"
TEST_PASSWORD="test123"
TEST_NAME="Test User ${TIMESTAMP}"
TEST_PHONE="010-${TIMESTAMP: -4}-${TIMESTAMP: -4}"

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 로그 함수
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# API 응답 파싱 함수
extract_token() {
    echo "$1" | grep -o '"token":"[^"]*"' | sed 's/"token":"//' | sed 's/"//'
}

extract_access_token() {
    echo "$1" | grep -o '"access_token":"[^"]*"' | sed 's/"access_token":"//' | sed 's/"//'
}

extract_points() {
    echo "$1" | grep -o '"points":[0-9]*' | sed 's/"points"://'
}

# 1단계: 사용자 등록
log_info "1단계: 새 사용자 등록 중..."
REGISTER_RESPONSE=$(curl -s --max-time 30 -X POST "${BASE_URL}/api/v1/auth/register-test" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "'${TEST_EMAIL}'",
    "password": "'${TEST_PASSWORD}'",
    "name": "'${TEST_NAME}'",
    "phoneNumber": "'${TEST_PHONE}'"
  }')

echo "등록 응답: $REGISTER_RESPONSE"

if echo "$REGISTER_RESPONSE" | grep -q '"code":200'; then
    log_success "사용자 등록 성공: $TEST_EMAIL"
else
    log_error "사용자 등록 실패"
    exit 1
fi

# 2단계: 로그인
log_info "2단계: 로그인 중..."
LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "'${TEST_EMAIL}'",
    "password": "'${TEST_PASSWORD}'"
  }')

echo "로그인 응답: $LOGIN_RESPONSE"

JWT_TOKEN=$(extract_token "$LOGIN_RESPONSE")
if [ -z "$JWT_TOKEN" ]; then
    log_error "JWT 토큰 추출 실패"
    exit 1
fi

log_success "JWT 토큰 발급 성공"

# 3단계: Passport 토큰 발급
log_info "3단계: Passport 토큰 발급 중..."
PASSPORT_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/auth/token/exchange" \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -H "Content-Type: application/json")

echo "Passport 응답: $PASSPORT_RESPONSE"

PASSPORT_TOKEN=$(extract_access_token "$PASSPORT_RESPONSE")
if [ -z "$PASSPORT_TOKEN" ]; then
    log_error "Passport 토큰 추출 실패"
    exit 1
fi

log_success "Passport 토큰 발급 성공"

# 4단계: 포인트 충전
log_info "4단계: 포인트 충전 중 (10,000포인트)..."
CHARGE_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/customers/me/points/charge" \
  -H "Authorization: Bearer ${PASSPORT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "points": 10000,
    "description": "테스트 포인트 충전"
  }')

echo "포인트 충전 응답: $CHARGE_RESPONSE"

# 포인트 충전은 500 에러가 나도 실제로는 충전되므로 프로필 확인으로 검증
log_info "포인트 충전 확인 중..."
PROFILE_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/v1/customers/profile" \
  -H "Authorization: Bearer ${PASSPORT_TOKEN}" \
  -H "Content-Type: application/json")

echo "프로필 응답: $PROFILE_RESPONSE"

CURRENT_POINTS=$(extract_points "$PROFILE_RESPONSE")
if [ "$CURRENT_POINTS" -ge 10000 ]; then
    log_success "포인트 충전 성공: ${CURRENT_POINTS}포인트"
else
    log_warning "포인트 충전 확인 필요: ${CURRENT_POINTS}포인트"
fi

# 5단계: 장바구니에 메뉴 추가
log_info "5단계: 장바구니에 메뉴 추가 중..."
CART_ADD_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/orders/cart/add" \
  -H "Authorization: Bearer ${PASSPORT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "menuId": "11111111-1111-1111-1111-111111111111",
    "menuName": "테스트 메뉴",
    "quantity": 2,
    "price": 5000,
    "storeId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
  }')

echo "장바구니 추가 응답: $CART_ADD_RESPONSE"

if echo "$CART_ADD_RESPONSE" | grep -q '"success":true'; then
    log_success "장바구니에 메뉴 추가 성공"
else
    log_error "장바구니 메뉴 추가 실패"
    exit 1
fi

# 6단계: 장바구니 확인
log_info "6단계: 장바구니 확인 중..."
CART_CHECK_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/v1/orders/cart" \
  -H "Authorization: Bearer ${PASSPORT_TOKEN}" \
  -H "Content-Type: application/json")

echo "장바구니 확인 응답: $CART_CHECK_RESPONSE"

if echo "$CART_CHECK_RESPONSE" | grep -q '"success":true'; then
    log_success "장바구니 확인 성공"
else
    log_error "장바구니 확인 실패"
    exit 1
fi

# 7단계: 주문 생성 (Saga 패턴)
log_info "7단계: 주문 생성 중 (Saga 패턴)..."
log_warning "주문 처리에 시간이 걸릴 수 있습니다..."

ORDER_RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}\n" -X POST "${BASE_URL}/api/v1/orders/saga" \
  -H "Authorization: Bearer ${PASSPORT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "storeId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    "orderType": "DELIVERY",
    "usePoints": true,
    "pointsToUse": 3000,
    "deliveryAddress": "서울특별시 강남구 테헤란로 123",
    "deliveryRequests": "문 앞에 놔주세요"
  }')

HTTP_STATUS=$(echo "$ORDER_RESPONSE" | grep "HTTP_STATUS" | cut -d: -f2)
ORDER_BODY=$(echo "$ORDER_RESPONSE" | sed '/HTTP_STATUS/d')

echo "주문 생성 응답 (HTTP $HTTP_STATUS): $ORDER_BODY"

# 8단계: 최종 상태 확인
log_info "8단계: 최종 포인트 잔액 확인 중..."
FINAL_PROFILE_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/v1/customers/profile" \
  -H "Authorization: Bearer ${PASSPORT_TOKEN}" \
  -H "Content-Type: application/json")

FINAL_POINTS=$(extract_points "$FINAL_PROFILE_RESPONSE")

echo "최종 프로필: $FINAL_PROFILE_RESPONSE"

# 결과 요약
echo ""
echo "=============================="
log_info "테스트 결과 요약"
echo "=============================="
echo "테스트 사용자: $TEST_EMAIL"
echo "초기 포인트: $CURRENT_POINTS"
echo "최종 포인트: $FINAL_POINTS"
echo "HTTP 상태: $HTTP_STATUS"

if [ "$HTTP_STATUS" = "200" ]; then
    log_success "주문 생성 성공!"
    USED_POINTS=$((CURRENT_POINTS - FINAL_POINTS))
    echo "사용된 포인트: $USED_POINTS"
elif [ "$HTTP_STATUS" = "504" ]; then
    log_warning "주문 요청 타임아웃 (Gateway Timeout)"
    echo "Saga 패턴이 처리 중일 수 있습니다. 포인트 변화를 확인하세요."
elif [ "$HTTP_STATUS" = "500" ]; then
    log_error "주문 처리 중 서버 오류 발생"
    echo "로그를 확인하여 구체적인 오류 원인을 파악하세요."
else
    log_error "예상치 못한 HTTP 상태: $HTTP_STATUS"
fi

# 포인트 변화 분석
if [ "$FINAL_POINTS" -lt "$CURRENT_POINTS" ]; then
    log_info "포인트가 차감되었습니다. Saga 패턴이 부분적으로 성공했을 수 있습니다."
elif [ "$FINAL_POINTS" -eq "$CURRENT_POINTS" ]; then
    log_info "포인트 변화 없음. 주문이 처리되지 않았거나 롤백되었습니다."
fi

echo "=============================="
echo ""

# 디버깅을 위한 추가 정보
log_info "디버깅 정보:"
echo "JWT 토큰: ${JWT_TOKEN:0:50}..."
echo "Passport 토큰: ${PASSPORT_TOKEN:0:50}..."
echo ""
echo "수동 재시도가 필요한 경우:"
echo "curl -X POST \"${BASE_URL}/api/v1/orders/saga\" \\"
echo "  -H \"Authorization: Bearer ${PASSPORT_TOKEN}\" \\"
echo "  -H \"Content-Type: application/json\" \\"
echo "  -d '{\"storeId\": \"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\", \"orderType\": \"DELIVERY\", \"usePoints\": true, \"pointsToUse\": 3000, \"deliveryAddress\": \"서울특별시 강남구 테헤란로 123\", \"deliveryRequests\": \"문 앞에 놔주세요\"}'"