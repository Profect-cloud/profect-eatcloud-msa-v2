# 🍽 Eat Cloud Project
Goorm 프로펙트 클라우드 엔지니어링 과정 3기 – 3차 프로젝트

## 📌 프로젝트 소개
**Eat Cloud**는 ‘배달의 민족’을 벤치마킹한 **주문 관리 플랫폼**입니다.
2차 프로젝트에서 모놀리식 아키텍처를 마이크로서비스로 분리하고, 기본적인 API 통신을 구현했습니다. 3차 프로젝트에서는 **이벤트 기반 아키텍처, Kubernetes, Observability**을 적용해 실제 운영 가능한 수준의 분산 시스템 구축했습니다.

## 📆 개발 기간
- 25.09.02 ~ 25.09.19

## 👥 멤버 구성
- [강능요](https://github.com/teadmu)
- [정연주](https://github.com/racoi)
- [정민영](https://github.com/minmaker-komu)
- [문창주](https://github.com/munstate)

## 🛠 기술 스택
`Java` `Spring Boot` `Spring Security` `PostgreSQL` `PostGIS` `Redis` `QueryDSL` `Spring Cloud` `Netflix Eureka` `Rlock` `Lua Script` `Apache Kafka` `AWS` `Kubernetes` `Helm` `Jenkins` `ArgoCD` `minikube` `kubectl` `EKS` `Docker` `Kinesis data streams` `Kinesis data analytics` `MSK` `RDS` `MongoDB` `Fluent Bit` `Prometheus` `Grafana` `Loki`

## ✨ 주요 기능
- Apache Kafka 활용 이벤트 기반 아키텍처 구축
- 주문 로직에 Saga Orchestration Pattern 적용
- Transactional Outbox Pattern 적용
- Kafka 이벤트 기반 재고 관리
- Redis 기반 분산락(Redisson + Lua Script)
- 이벤트 소싱, CQRS 패턴으로 조회와 명령 분리
- MDC + HTTP Filter 기반 분산 요청 추적
- AWS Kinesis를 통한 실시간 이벤트 스트리밍

## 🏗 아키텍처
### 디렉토리 구조
```
profect-eatcloud-msa-v2/
├─ admin-service/         # 관리자 서비스
├─ api-gateway/           # 라우팅/문서 허브
├─ auth-service/          # 인증/인가
├─ auto-response/         # 공통 응답/에러 처리 라이브러리
├─ auto-time/             # JPA 시간 감시 공통 모듈
├─ customer-service/      # 고객 도메인
├─ database-init/         # 초기 스키마/데이터 SQL
├─ deploy/                # 배포/컴포즈/환경 변수 템플릿
├─ eureka-server/         # 서비스 디스커버리
├─ manager-service/       # 매니저 도메인
├─ order-service/         # 주문 도메인
├─ payment-service/       # 결제 도메인
├─ store-service/         # 점포 도메인
├─ docker-compose.yml     # 개발용 간단 실행
└─ build.gradle           # 루트 그레이들 구성
```
### 인프라 아키텍처
![git\_readme\_img-001](https://github.com/user-attachments/assets/b7fbf51e-8a08-4a79-97c3-a2e7bdb66672)
### CI/CD 아키텍처
![git\_readme\_img-002](https://github.com/user-attachments/assets/d840e706-1ce6-4347-acf1-2c55231600fa)
### 로깅 아키텍처
<img width="870" height="615" alt="Image" src="https://github.com/user-attachments/assets/52b1a4de-c01c-491f-9218-815b09ceec2d" />
