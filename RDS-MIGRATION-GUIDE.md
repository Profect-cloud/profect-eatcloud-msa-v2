# Single RDS Migration Guide

## 개요
이 프로젝트는 기존의 여러 개별 데이터베이스에서 **단일 RDS 인스턴스**를 사용하도록 마이그레이션되었습니다.

## 변경 사항

### 1. 데이터베이스 구조 변경
- **이전**: 각 서비스별 개별 PostgreSQL 컨테이너
  - user-db (사용자 관리)
  - store-db (상점 관리) 
  - order-db (주문 관리)
  - payment-db (결제 관리)

- **현재**: 단일 PostgreSQL 인스턴스 내 여러 데이터베이스
  - eatcloud-db 컨테이너 내에 auth_db, customer_db, admin_db, owner_db, store_db, order_db, payment_db 스키마

### 2. 환경 설정 변경

#### 개발 환경
```bash
# 기본 실행
docker-compose up

# 또는 명시적 환경 지정
docker-compose -f deploy/compose/.yml -f deploy/compose/dev/.yml up
```

#### 프로덕션 환경
```bash
# AWS RDS 사용 (로컬 DB 컨테이너 비활성화)
docker-compose -f deploy/compose/.yml -f deploy/compose/prod/.yml up
```

### 3. 데이터베이스 연결 정보

#### 개발 환경
- **호스트**: eatcloud-db (Docker 네트워크 내)
- **포트**: 5432
- **사용자**: eatcloud_user
- **비밀번호**: devpassword123
- **데이터베이스들**: auth_db, customer_db, admin_db, owner_db, store_db, order_db, payment_db

#### 프로덕션 환경 (AWS RDS)
- **호스트**: your-rds-instance.region.rds.amazonaws.com
- **포트**: 5432
- **사용자**: eatcloud_admin
- **비밀번호**: your-secure-production-password
- **SSL**: 필수

## AWS RDS 설정 가이드

### 1. RDS 인스턴스 생성
```bash
# AWS CLI를 사용한 RDS 인스턴스 생성 예시
aws rds create-db-instance \
    --db-instance-identifier eatcloud-prod-db \
    --db-instance-class db.t3.micro \
    --engine postgres \
    --engine-version 17.0 \
    --master-username eatcloud_admin \
    --master-user-password your-secure-password \
    --allocated-storage 20 \
    --storage-type gp2 \
    --vpc-security-group-ids sg-xxxxxxxxxx \
    --db-subnet-group-name your-subnet-group \
    --backup-retention-period 7 \
    --storage-encrypted
```

### 2. 데이터베이스 생성
RDS 인스턴스에 연결 후 다음 스키마들을 생성:

```sql
-- 각 서비스별 데이터베이스 생성
CREATE DATABASE auth_db;
CREATE DATABASE customer_db;
CREATE DATABASE admin_db;
CREATE DATABASE owner_db;
CREATE DATABASE store_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;

-- 권한 부여
GRANT ALL PRIVILEGES ON DATABASE auth_db TO eatcloud_admin;
GRANT ALL PRIVILEGES ON DATABASE customer_db TO eatcloud_admin;
GRANT ALL PRIVILEGES ON DATABASE admin_db TO eatcloud_admin;
GRANT ALL PRIVILEGES ON DATABASE owner_db TO eatcloud_admin;
GRANT ALL PRIVILEGES ON DATABASE store_db TO eatcloud_admin;
GRANT ALL PRIVILEGES ON DATABASE order_db TO eatcloud_admin;
GRANT ALL PRIVILEGES ON DATABASE payment_db TO eatcloud_admin;
```

### 3. 환경 변수 설정
프로덕션 환경 변수 파일들을 실제 RDS 정보로 업데이트:

- `deploy/env/prod/eatcloud-rds.env`
- `deploy/env/prod/auth-service.env`
- `deploy/env/prod/customer-service.env`
- `deploy/env/prod/admin-service.env`
- `deploy/env/prod/owner-service.env`
- `deploy/env/prod/store-service.env`
- `deploy/env/prod/order-service.env`
- `deploy/env/prod/payment-service.env`

## 마이그레이션 체크리스트

### 개발 환경
- [x] 단일 PostgreSQL 컨테이너 설정
- [x] 다중 데이터베이스 생성 스크립트
- [x] 개발용 환경 변수 파일 생성
- [x] Docker Compose 설정 업데이트

### 프로덕션 환경  
- [ ] AWS RDS 인스턴스 생성
- [ ] RDS 보안 그룹 설정
- [ ] SSL 인증서 설정
- [ ] 백업 및 모니터링 설정
- [ ] 프로덕션 환경 변수 업데이트
- [ ] 데이터 마이그레이션 (기존 데이터가 있는 경우)

## 서비스별 연결 정보

각 마이크로서비스는 동일한 RDS 인스턴스에 연결하되, 각자의 데이터베이스 스키마를 사용합니다:

| 서비스 | 데이터베이스 | 포트 |
|--------|--------------|------|
| auth-service | auth_db | 8081 |
| customer-service | customer_db | 8082 |
| admin-service | admin_db | 8083 |
| owner-service | owner_db | 8084 |
| store-service | store_db | 8085 |
| order-service | order_db | 8086 |
| payment-service | payment_db | 8087 |

## 모니터링 및 유지보수

### 개발 환경
```bash
# 데이터베이스 상태 확인
docker logs eatcloud-db

# 데이터베이스 접속
docker exec -it eatcloud-db psql -U eatcloud_user -d eatcloud_db
```

### 프로덕션 환경
- AWS RDS 콘솔에서 성능 모니터링
- CloudWatch 메트릭 확인
- 정기적인 백업 확인

## 트러블슈팅

### 연결 문제
1. 네트워크 연결 확인
2. 보안 그룹 설정 확인 (프로덕션)
3. SSL 설정 확인 (프로덕션)
4. 인증 정보 확인

### 성능 문제
1. 연결 풀 설정 조정
2. 쿼리 성능 분석
3. 인덱스 최적화
4. RDS 인스턴스 크기 조정 (프로덕션)

## 비용 최적화 (AWS)

1. **RDS 인스턴스 크기**: 트래픽에 맞는 적절한 크기 선택
2. **스토리지 타입**: gp2 vs gp3 비교
3. **백업 보존 기간**: 필요에 따라 조정
4. **리드 레플리카**: 읽기 성능 개선 시 고려
5. **Reserved Instance**: 장기간 사용 시 비용 절감

## 다음 단계

1. 개발 환경에서 테스트
2. 스테이징 환경 구성
3. 프로덕션 RDS 설정
4. 데이터 마이그레이션
5. 모니터링 및 알람 설정
