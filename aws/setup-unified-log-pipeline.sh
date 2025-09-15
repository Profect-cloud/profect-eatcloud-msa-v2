#!/bin/bash

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== 통합 로그 파이프라인 설정 ===${NC}"
echo -e "${YELLOW}Kinesis Analytics (추천) + Firehose (모니터링) 동시 구성${NC}"
echo ""

# 변수 설정
ACCOUNT_ID="