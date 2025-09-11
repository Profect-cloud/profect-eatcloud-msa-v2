# 🚀 EatCloud MSA Kinesis 로깅 파이프라인

## 📋 개요

3개 토픽 기반 Kinesis 파이프라인을 통한 실시간 로그 처리 시스템입니다.

### 🎯 3개 Kinesis Stream

1. **eatcloud-stateful-logs** → Kinesis Analytics (실시간 집계)
2. **eatcloud-stateless-logs** → Vector (저장/검색)  
3. **eatcloud-recommendation-events** → MongoDB (실시간 추천)

### 🏗️ 아키텍처

```
Spring Boot Apps → Logback → Fluent Bit → Kinesis Streams → 후처리
     ↓              ↓         ↓           ↓
  LoggingAspect   3개 파일    사이드카    목적별 라우팅
```

## 🚀 빠른 배포

```bash
# 1. 권한 설정 및 Kinesis 생성
./setup-fluent-bit-iam.sh

# 2. 빠른 배포 (권장)
./quick-start.sh

# 3. 상태 확인
kubectl get pods -n dev
```

## 📁 파일 구조

- `01-fluent-bit-configmap.yaml` - Fluent Bit 설정
- `02-fluent-bit-rbac.yaml` - ServiceAccount & RBAC
- `03-admin-service-deployment.yaml` - Admin Service + 사이드카
- `04-customer-service-deployment.yaml` - Customer Service + 사이드카
- `05-eatcloud-ingress.yaml` - Internal ALB Ingress
- `setup-fluent-bit-iam.sh` - IAM & Kinesis 생성
- `deploy.sh` - 상세 배포 스크립트
- `quick-start.sh` - 빠른 배포 스크립트
- `troubleshoot.sh` - 문제 해결 도구

## 🔧 문제 해결

```bash
# 자동 진단
./troubleshoot.sh

# 로그 확인
kubectl logs -n dev <pod-name> -c fluent-bit
```

## 📊 모니터링

```bash
# Fluent Bit 메트릭
kubectl port-forward -n dev <pod-name> 2020:2020
# http://localhost:2020/api/v1/health
```
