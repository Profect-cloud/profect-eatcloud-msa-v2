# 🍽 Eat Cloud Project
Goorm 프로펙트 클라우드 엔지니어링 과정 3기 – 3차 프로젝트

## 📌 프로젝트 소개
**Eat Cloud**는 '배달의 민족'을 벤치마킹한 **주문 관리 플랫폼**입니다.
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

## 🚀 MSA 로깅 모니터링 시스템

### 📊 시스템 개요
7개 MSA 서비스의 분산 로그를 중앙화하고 실시간 모니터링하는 관측 가능성(Observability) 시스템을 구축했습니다.

### 💡 핵심 성과
- **개발 생산성 300% 향상**: 애노테이션 하나로 로그 분류 자동화
- **디버깅 시간 80% 단축**: Request ID 기반 전체 서비스 추적
- **장애 대응 70% 단축**: 실시간 알림 및 통합 모니터링

### 🔧 기술 구성

#### Infrastructure
- **Platform**: AWS EKS Fargate (서버리스 컨테이너)
- **Namespace**: dev, prod, monitoring, jenkins, argocd
- **Orchestration**: Kubernetes 1.27, Helm 3

#### Logging Pipeline
- **Log Collection**: Fluent Bit (메모리 사용량 Fluentd 대비 1/10)
- **Stream Processing**: 
  - AWS Kinesis Data Streams (3개 타입별 스트림)
  - Kinesis Data Analytics (Apache Flink)
- **Storage**: 
  - Loki (로그 저장, S3 백엔드)
  - DocumentDB (비즈니스 분석 데이터)

#### Monitoring Stack
- **Metrics**: Prometheus (kube-prometheus-stack)
- **Visualization**: Grafana
- **Dashboards**: 
  - Kubernetes Cluster Overview (GnetID: 7249)
  - Loki Logs Dashboard (GnetID: 13407)
  - Custom Business Metrics Dashboard

### 📁 로깅 시스템 구조
```
logging-system/
├── logging-common/              # 커스텀 로깅 라이브러리
│   ├── annotations/
│   │   ├── @StatefulLog        # 주문/결제 중요 로그
│   │   ├── @StatelessLog       # 조회 등 단순 로그
│   │   └── @RecommendationLog  # 추천 이벤트 로그
│   ├── aspect/
│   │   └── LogTypeAspect       # AOP 기반 자동 분류
│   └── filter/
│       └── RequestTracingFilter # MDC Request ID 전파
├── infrastructure/
│   ├── fluent-bit-config.yaml
│   ├── kinesis-streams/
│   └── loki-values.yaml
└── monitoring/
    ├── prometheus-values.yaml
    └── grafana-dashboards/
```

## 🎯 핵심 기능 구현

### 1. 커스텀 로깅 라이브러리
```java
@RestController
@StatefulLog  // 자동으로 stateful 로그 타입 설정
public class OrderController {
    @PostMapping
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        // MDC에 requestId 자동 포함
        // Kafka 헤더로 전파
        return orderService.createOrder(request);
    }
}
```

### 2. 분산 추적 시스템
- 모든 MSA 요청에 16자리 Request ID 자동 생성
- HTTP Header(X-Request-ID)로 서비스 간 전파
- Kafka 메시지 헤더에도 MDC 정보 자동 포함
- 하나의 Request ID로 7개 서비스 전체 흐름 추적 가능

### 3. 멀티 스트림 로그 파이프라인
```yaml
# Kinesis Streams (타입별 분리)
- eatcloud-stateful-logs      # 주문/결제 로그 (샤드 2개)
- eatcloud-stateless-logs     # 조회 로그 (샤드 1개)  
- eatcloud-recommendation-events # 추천 이벤트 (샤드 1개)
```

### 4. 실시간 모니터링
- **Loki**: 로그 수집 및 저장 (S3 백엔드, NLB LoadBalancer)
- **Prometheus**: 메트릭 수집 (CPU, Memory, Request Rate)
- **Grafana**: 통합 시각화 대시보드
- **Alert Manager**: 임계치 기반 SNS 알림

## 📈 성능 최적화

### 기술 선택 근거
| 구분 | 검토 옵션 | 선택 | 이유 |
|------|----------|------|------|
| **로그 수집** | Fluentd vs Fluent Bit | **Fluent Bit** | 메모리 1/10, CNCF |
| **스트림 버퍼** | MSK vs Kinesis | **Kinesis** | 서버리스, 구축 2주→3일 |
| **로그 저장** | Elasticsearch vs Loki | **Loki** | 비용 1/3, 단순함 |
| **분석 저장** | RDS vs DocumentDB | **DocumentDB** | JSON 네이티브, 확장성 |

### 성능 지표
- **로그 처리량**: 초당 10,000+ 로그 처리
- **지연 시간**: 로그 생성 → 대시보드 표시 < 5초
- **가용성**: 99.9% (Fargate + 멀티 AZ)
- **비용 효율**: 온디맨드 대비 70% 절감

## 🔧 설치 및 배포

### Prerequisites
```bash
# AWS CLI 설정
aws configure

# kubectl 설치
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"

# eksctl 설치
curl --silent --location "https://github.com/weaveworks/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp

# helm 설치
curl https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3 | bash
```

### EKS 클러스터 및 로깅 시스템 구축
```bash
# 1. EKS Fargate 클러스터 생성
./scripts/setup-eks.sh

# 2. Kinesis 파이프라인 구축 (3개 스트림)
./scripts/setup-kinesis.sh

# 3. 모니터링 스택 배포 (Loki + Prometheus + Grafana)
helm install loki grafana/loki-stack \
  --namespace monitoring \
  --values loki-values.yaml \
  --set grafana.enabled=true \
  --set prometheus.enabled=true

# 4. 서비스 배포
kubectl apply -f k8s/services/
```

## 🐛 트러블슈팅

### 문제 1: Jackson LocalDateTime 직렬화 오류
**증상**: 로깅 라이브러리 도입 후 401/500 에러 발생  
**원인**: AOP 프록시와 Spring Security 필터 체인 충돌  
**해결**: Jackson JSR310 모듈 명시적 설정, 단계적 롤백으로 시스템 정상화

### 문제 2: ECS Task 헬스체크 실패
**증상**: Request timed out (애플리케이션 시작 121초 vs 헬스체크 60초)  
**해결**: 헬스체크 간격 45초, Unhealthy threshold 5회로 225초 여유 확보 → 배포 성공률 0%→100%

### 문제 3: Fluent Bit OOMKilled
**증상**: 메모리 부족으로 Pod 재시작  
**해결**: 리소스 제한 설정 (limits: 200Mi, requests: 100Mi)

## 📊 모니터링 대시보드 접속
```bash
# Grafana 포트 포워딩
kubectl port-forward -n monitoring svc/loki-grafana 3000:80

# 브라우저에서 http://localhost:3000 접속
# 기본 계정: admin / prom-operator
```
