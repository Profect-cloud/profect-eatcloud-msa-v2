# 🚀 서비스 배포 가이드

## 📝 서비스별 배포 상태
- ✅ **admin-service** (8083) - 이미 배포됨
- ⏳ **auth-service** (8081) - 배포 대기
- ⏳ **customer-service** (8082) - 배포 대기  
- ⏳ **order-service** (8084) - 배포 대기
- ⏳ **payment-service** (8085) - 배포 대기
- ⏳ **store-service** (8086) - 배포 대기

## 🎯 빠른 배포 명령어

### 1️⃣ 개별 서비스 배포
```bash
# Auth Service 배포
./k8s-fluent-bit/deploy-individual-services.sh auth-service

# Customer Service 배포
./k8s-fluent-bit/deploy-individual-services.sh customer-service

# Order Service 배포
./k8s-fluent-bit/deploy-individual-services.sh order-service

# Payment Service 배포
./k8s-fluent-bit/deploy-individual-services.sh payment-service

# Store Service 배포
./k8s-fluent-bit/deploy-individual-services.sh store-service
```

### 2️⃣ 모든 서비스 한번에 배포
```bash
./k8s-fluent-bit/deploy-individual-services.sh all
```

## 📋 수동 배포 단계별 가이드

### Auth Service (8081) 배포

1. **이미지 빌드 & 푸시**
```bash
# Gradle 빌드
./gradlew :auth-service:clean :auth-service:build -x test

# ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com

# Docker 빌드 (AMD64)
docker buildx build --platform linux/amd64 -t auth-service ./auth-service

# 이미지 태그 & 푸시
docker tag auth-service:latest 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com/eatcloud/auth-service:latest
docker push 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com/eatcloud/auth-service:latest
```

2. **Kubernetes 배포**
```bash
kubectl apply -f k8s-fluent-bit/06-auth-service-deployment.yaml
```

3. **상태 확인**
```bash
kubectl get pods -n dev -l app=auth-service
kubectl logs -f deployment/auth-service -c auth-service -n dev
```

### Customer Service (8082) 배포

1. **이미지 빌드 & 푸시**
```bash
./gradlew :customer-service:clean :customer-service:build -x test
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com
docker buildx build --platform linux/amd64 -t customer-service ./customer-service
docker tag customer-service:latest 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com/eatcloud/customer-service:latest
docker push 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com/eatcloud/customer-service:latest
```

2. **Kubernetes 배포**
```bash
kubectl apply -f k8s-fluent-bit/07-customer-service-deployment.yaml
```

### Order Service (8084) 배포

1. **이미지 빌드 & 푸시**
```bash
./gradlew :order-service:clean :order-service:build -x test
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com
docker buildx build --platform linux/amd64 -t order-service ./order-service
docker tag order-service:latest 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com/eatcloud/order-service:latest
docker push 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com/eatcloud/order-service:latest
```

2. **Kubernetes 배포**
```bash
kubectl apply -f k8s-fluent-bit/08-order-service-deployment.yaml
```

### Payment Service (8085) 배포

1. **이미지 빌드 & 푸시**
```bash
./gradlew :payment-service:clean :payment-service:build -x test
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com
docker buildx build --platform linux/amd64 -t payment-service ./payment-service
docker tag payment-service:latest 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com/eatcloud/payment-service:latest
docker push 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com/eatcloud/payment-service:latest
```

2. **Kubernetes 배포**
```bash
kubectl apply -f k8s-fluent-bit/09-payment-service-deployment.yaml
```

### Store Service (8086) 배포

1. **이미지 빌드 & 푸시**
```bash
./gradlew :store-service:clean :store-service:build -x test
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com
docker buildx build --platform linux/amd64 -t store-service ./store-service
docker tag store-service:latest 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com/eatcloud/store-service:latest
docker push 536580887516.dkr.ecr.ap-northeast-2.amazonaws.com/eatcloud/store-service:latest
```

2. **Kubernetes 배포**
```bash
kubectl apply -f k8s-fluent-bit/10-store-service-deployment.yaml
```

## 🔍 전체 상태 확인

### Pod 상태 확인
```bash
kubectl get pods -n dev
```

### 서비스 상태 확인  
```bash
kubectl get svc -n dev
```

### 로그 확인
```bash
# 전체 서비스 로그 확인
kubectl get pods -n dev | grep -v test-nginx | grep -v NAME | while read pod rest; do
  echo "=== $pod 로그 ==="
  kubectl logs $pod -c fluent-bit -n dev --tail=5
  echo ""
done

# 특정 서비스 로그 확인
kubectl logs -f deployment/[service-name] -c [service-name] -n dev
kubectl logs -f deployment/[service-name] -c fluent-bit -n dev
```

## 🚨 트러블슈팅

### ImagePullBackOff 문제
```bash
# AMD64 아키텍처로 다시 빌드
docker buildx build --platform linux/amd64 -t [service-name] ./[service-name]
```

### CrashLoopBackOff 문제
```bash
# 로그 확인
kubectl logs [pod-name] -c [service-name] -n dev --tail=50

# livenessProbe 제거 후 재배포
```

### 네트워킹 문제
```bash
# 서비스 엔드포인트 확인
kubectl get endpoints -n dev

# Pod 간 통신 테스트
kubectl exec -it [pod-name] -n dev -- curl http://[service-name].[namespace].svc.cluster.local/actuator/health
```

## 📊 성능 모니터링

### 리소스 사용량 확인
```bash
kubectl top pods -n dev
kubectl top nodes
```

### 서비스 헬스체크
```bash
for service in auth-service customer-service order-service payment-service store-service; do
  echo "=== $service 헬스체크 ==="
  kubectl exec -it test-nginx -n dev -- curl -s http://$service.dev.svc.cluster.local/actuator/health | jq .
  echo ""
done
```

## 🎉 배포 완료 후 확인사항

1. ✅ 모든 Pod가 Running 상태인지 확인
2. ✅ 각 서비스의 헬스체크가 정상인지 확인  
3. ✅ Fluent Bit 로깅이 Kinesis로 정상 전송되는지 확인
4. ✅ 서비스 간 통신이 정상인지 확인
5. ✅ 리소스 사용량이 적정 수준인지 확인

---
**💡 팁**: 자동 배포 스크립트를 사용하면 더 안전하고 빠르게 배포할 수 있습니다!
