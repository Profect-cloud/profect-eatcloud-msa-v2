#!/bin/bash

# EatCloud MSA v1 - 권한 설정 스크립트

echo "🔧 스크립트 파일 권한 설정 중..."

# 실행 스크립트들에 실행 권한 부여
chmod +x run-dev.sh
chmod +x run-prod.sh
chmod +x setup-permissions.sh
chmod +x deploy/compose/scripts/create-multiple-databases.sh

# Gradle 래퍼에 실행 권한 부여
chmod +x gradlew
chmod +x api-gateway/gradlew
chmod +x eureka-server/gradlew

echo "✅ 권한 설정 완료!"
echo ""
echo "📋 실행 가능한 스크립트들:"
echo "  ./run-dev.sh          - 개발 환경 실행"
echo "  ./run-prod.sh         - 프로덕션 환경 실행"
echo "  ./gradlew build       - 프로젝트 빌드"
echo ""
echo "🐳 Docker Compose 명령어:"
echo "  docker-compose up                                           - 기본 실행"
echo "  docker-compose -f deploy/compose/.yml -f deploy/compose/dev/.yml up  - 개발 환경"
echo "  docker-compose -f deploy/compose/.yml -f deploy/compose/prod/.yml up - 프로덕션 환경"
