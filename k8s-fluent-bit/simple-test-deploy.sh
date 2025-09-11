#!/bin/bash

# 🚀 EatCloud MSA 로깅 시스템 간단 테스트 배포
# 
# 이미 빌드된 이미지를 사용하여 빠르게 배포하고 로깅 테스트

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# 변수 정의
NAMESPACE="dev"
AWS_REGION="ap-northeast-2"
AWS_ACCOUNT_ID="536580887516"
ECR_BASE="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/eatcloud"

echo -e "${BLUE}🚀 EatCloud MSA 로깅 시스템 간단 테스트 배포를 시작합니다...${NC}"

# 1. 테스트 서비스 배포 YAML 생성
echo -e "${YELLOW}📝 테스트 배포 YAML 생성 중...${NC}"

cat > simple-test-deployment.yaml << EOF
# Admin Service
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: admin-service
  namespace: ${NAMESPACE}
  labels:
    app: admin-service
    version: v1
spec:
  replicas: 1
  selector:
    matchLabels:
      app: admin-service
  template:
    metadata:
      labels:
        app: admin-service
        version: v1
    spec:
      serviceAccountName: fluent-bit-service-account
      containers:
      # 메인 애플리케이션 컨테이너
      - name: admin-service
        image: ${ECR_BASE}/admin-service:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8081
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "dev"
        - name: LOG_PATH
          value: "/var/log/app"
        - name: SPRING_APPLICATION_NAME
          value: "admin-service"
        - name: LOGGING_RECOMMENDATION_ENABLED
          value: "true"
        envFrom:
        - configMapRef:
            name: complete-configmap
        - secretRef:
            name: app-secrets
        volumeMounts:
        - name: app-logs
          mountPath: /var/log/app
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 90
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 5
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3

      # Fluent Bit 사이드카 컨테이너
      - name: fluent-bit
        image: fluent/fluent-bit:2.2.0
        imagePullPolicy: Always
        ports:
        - containerPort: 2020
          name: http-metrics
        volumeMounts:
        - name: fluent-bit-config
          mountPath: /fluent-bit/etc
        - name: app-logs
          mountPath: /var/log/app
          readOnly: true
        - name: fluent-bit-db
          mountPath: /fluent-bit/tail
        - name: fluent-bit-logs
          mountPath: /var/log/fluent-bit
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "200m"
        livenessProbe:
          httpGet:
            path: /api/v1/health
            port: 2020
          initialDelaySeconds: 30
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /api/v1/health
            port: 2020
          initialDelaySeconds: 10
          periodSeconds: 10

      volumes:
      - name: app-logs
        emptyDir: {}
      - name: fluent-bit-db
        emptyDir: {}
      - name: fluent-bit-logs
        emptyDir: {}
      - name: fluent-bit-config
        configMap:
          name: fluent-bit-config

---
apiVersion: v1
kind: Service
metadata:
  name: admin-service
  namespace: ${NAMESPACE}
  labels:
    app: admin-service
spec:
  ports:
  - port: 80
    targetPort: 8081
    protocol: TCP
    name: http
  - port: 2020
    targetPort: 2020
    protocol: TCP
    name: metrics
  selector:
    app: admin-service
  type: ClusterIP

# Customer Service
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: customer-service
  namespace: ${NAMESPACE}
  labels:
    app: customer-service
    version: v1
spec:
  replicas: 1
  selector:
    matchLabels:
      app: customer-service
  template:
    metadata:
      labels:
        app: customer-service
        version: v1
    spec:
      serviceAccountName: fluent-bit-service-account
      containers:
      # 메인 애플리케이션 컨테이너
      - name: customer-service
        image: ${ECR_BASE}/customer-service:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8082
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "dev"
        - name: LOG_PATH
          value: "/var/log/app"
        - name: SPRING_APPLICATION_NAME
          value: "customer-service"
        - name: LOGGING_RECOMMENDATION_ENABLED
          value: "true"
        envFrom:
        - configMapRef:
            name: complete-configmap
        - secretRef:
            name: app-secrets
        volumeMounts:
        - name: app-logs
          mountPath: /var/log/app
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8082
          initialDelaySeconds: 90
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 5
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8082
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3

      # Fluent Bit 사이드카 컨테이너
      - name: fluent-bit
        image: fluent/fluent-bit:2.2.0
        imagePullPolicy: Always
        ports:
        - containerPort: 2020
          name: http-metrics
        volumeMounts:
        - name: fluent-bit-config
          mountPath: /fluent-bit/etc
        - name: app-logs
          mountPath: /var/log/app
          readOnly: true
        - name: fluent-bit-db
          mountPath: /fluent-bit/tail
        - name: fluent-bit-logs
          mountPath: /var/log/fluent-bit
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "200m"
        livenessProbe:
          httpGet:
            path: /api/v1/health
            port: 2020
          initialDelaySeconds: 30
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /api/v1/health
            port: 2020
          initialDelaySeconds: 10
          periodSeconds: 10

      volumes:
      - name: app-logs
        emptyDir: {}
      - name: fluent-bit-db
        emptyDir: {}
      - name: fluent-bit-logs
        emptyDir: {}
      - name: fluent-bit-config
        configMap:
          name: fluent-bit-config

---
apiVersion: v1
kind: Service
metadata:
  name: customer-service
  namespace: ${NAMESPACE}
  labels:
    app: customer-service
spec:
  ports:
  - port: 80
    targetPort: 8082
    protocol: TCP
    name: http
  - port: 2020
    targetPort: 2020
    protocol: TCP
    name: metrics
  selector:
    app: customer-service
  type: ClusterIP
EOF

# 2. ConfigMap 및 RBAC 적용
echo -e "${YELLOW}📋 Fluent Bit 설정 적용 중...${NC}"
kubectl apply -f 01-fluent-bit-configmap-local.yaml
kubectl apply -f 02-fluent-bit-rbac-local.yaml

# 3. 서비스 배포
echo -e "${YELLOW}🚀 서비스 배포 중...${NC}"
kubectl apply -f simple-test-deployment.yaml

# 4. Pod 시작 대기
echo -e "${YELLOW}⏳ Pod가 시작될 때까지 대기 중...${NC}"
echo -e "${BLUE}🔄 Admin Service Pod 대기...${NC}"
kubectl wait --for=condition=Ready pod -l app=admin-service -n ${NAMESPACE} --timeout=300s || true

echo -e "${BLUE}🔄 Customer Service Pod 대기...${NC}"
kubectl wait --for=condition=Ready pod -l app=customer-service -n ${NAMESPACE} --timeout=300s || true

# 5. 상태 확인
echo -e "${GREEN}🎉 배포가 완료되었습니다!${NC}"
echo ""
echo -e "${BLUE}📊 Pod 상태:${NC}"
kubectl get pods -n ${NAMESPACE}

echo ""
echo -e "${BLUE}🔍 로깅 검증을 위한 명령어:${NC}"
echo -e "  • ./verify-logging.sh 실행"
echo -e "  • kubectl logs <pod-name> -n ${NAMESPACE} -c fluent-bit"
echo -e "  • kubectl exec <pod-name> -n ${NAMESPACE} -c <service-name> -- ls -la /var/log/app/"

# 임시 파일 정리
rm -f simple-test-deployment.yaml

echo ""
echo -e "${GREEN}✨ 간단 테스트 배포가 완료되었습니다!${NC}"
