#!/bin/bash

# EatCloud MSA v1 - 개발 환경 실행 스크립트 (단일 RDS 버전)

echo "🚀 EatCloud MSA v1 개발 환경을 시작합니다..."

# 환경 변수 파일 로드 (주석과 빈 줄 제외)
if [ -f "deploy/env/dev/.env" ]; then
    export $(grep -v '^#' deploy/env/dev/.env | grep -v '^$' | xargs)
    echo "✅ 개발 환경 변수 로드 완료 (deploy/env/dev/.env)"
else
    echo "⚠️  deploy/env/dev/.env 파일이 없습니다. 기본 설정으로 진행합니다."
fi

# 로그 디렉토리 생성
mkdir -p logs
echo "📁 로그 디렉토리 생성 완료"

# Docker Compose 실행 (공통 + 개발 환경)
echo "🐳 Docker Compose로 서비스를 시작합니다..."
docker-compose -f deploy/compose/.yml -f deploy/compose/dev/.yml up --build

echo ""
echo "✅ 개발 환경이 시작되었습니다!"
echo "==========================================="
echo "📊 Eureka Server: http://localhost:8761"
echo "🌐 API Gateway: http://localhost:8080"
echo "🔐 Auth Service: http://localhost:8081"
echo "👤 Customer Service: http://localhost:8082"
echo "👨‍💼 Admin Service: http://localhost:8083"
echo "🏪 Owner Service: http://localhost:8084"
echo "🏬 Store Service: http://localhost:8085"
echo "📦 Order Service: http://localhost:8086"
echo "💳 Payment Service: http://localhost:8087"
echo "🗄️ Redis: localhost:6379"
echo "🗃️ PostgreSQL (통합): localhost:5432"
echo "   - auth_db, customer_db, admin_db, owner_db, store_db, order_db, payment_db"
echo "==========================================="
echo ""
echo "📝 데이터베이스 접속 정보:"
echo "   Host: localhost"
echo "   Port: 5432"
echo "   User: eatcloud_user"
echo "   Password: devpassword123"
echo "   Databases: auth_db, customer_db, admin_db, owner_db, store_db, order_db, payment_db"
echo ""
echo "🛠️ 데이터베이스 접속 명령어:"
echo "   docker exec -it eatcloud-db psql -U eatcloud_user -d eatcloud_db"
echo "   \\l  # 데이터베이스 목록 확인"
echo "   \\c order_db  # order_db로 연결"
echo "==========================================="
echo ""
echo "🔍 현재 활성화된 서비스:"
echo "   - Eureka Server ✅"
echo "   - API Gateway ✅"  
echo "   - Order Service ✅"
echo "   - Redis ✅"
echo "   - PostgreSQL ✅"
echo ""
echo "⚠️  아직 개발 중인 서비스:"
echo "   - Auth Service (포트 8081)"
echo "   - Customer Service (포트 8082)"
echo "   - Admin Service (포트 8083)"
echo "   - Owner Service (포트 8084)"
echo "   - Store Service (포트 8085)"
echo "   - Payment Service (포트 8087)"
echo ""
echo "📋 서비스별 Health Check URL:"
echo "   - Order Service: http://localhost:8086/orders/health"
echo "==========================================="
