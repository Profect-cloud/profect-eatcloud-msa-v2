#!/bin/bash

# 기존 Loki 서비스 문제 해결 스크립트

echo "=== 기존 Loki 서비스 문제 해결 ==="

echo "1. 현재 Loki 서비스 상태 확인"
kubectl get svc -n monitoring | grep loki

echo -e "\n2. Loki 서비스 상세 정보"
kubectl describe svc loki -n monitoring 2>/dev/null || {
    echo "loki 서비스를 찾을 수 없습니다. 모든 서비스 확인:"
    kubectl get svc -n monitoring
}

echo -e "\n3. Loki Pod 상태 및 포트 확인"
kubectl get pod loki-0 -n monitoring -o wide
kubectl describe pod loki-0 -n monitoring | grep -A 5 -B 5 Port

echo -e "\n4. 현재 LoadBalancer 서비스들"
kubectl get svc -n monitoring --field-selector spec.type=LoadBalancer

echo -e "\n5. Loki 서비스 타입 확인 및 LoadBalancer로 변경"
CURRENT_TYPE=$(kubectl get svc loki -n monitoring -o jsonpath='{.spec.type}' 2>/dev/null)
if [ "$CURRENT_TYPE" = "LoadBalancer" ]; then
    echo "이미 LoadBalancer 타입입니다."
    EXTERNAL_IP=$(kubectl get svc loki -n monitoring -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
    if [ -z "$EXTERNAL_IP" ] || [ "$EXTERNAL_IP" = "null" ]; then
        echo "LoadBalancer 주소가 할당되지 않았습니다. 재생성 중..."
        kubectl delete svc loki -n monitoring
        sleep 5
        
        # LoadBalancer 서비스 생성
        cat << EOF | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: loki
  namespace: monitoring
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-type: nlb
    service.beta.kubernetes.io/aws-load-balancer-scheme: internet-facing
spec:
  type: LoadBalancer
  ports:
  - name: http
    port: 3100
    targetPort: 3100
    protocol: TCP
  selector:
    app.kubernetes.io/name: loki
EOF
    fi
else
    echo "현재 서비스 타입: $CURRENT_TYPE"
    echo "LoadBalancer로 변경 중..."
    
    # 서비스 타입을 LoadBalancer로 변경
    kubectl patch svc loki -n monitoring -p '{"spec":{"type":"LoadBalancer"}}' 2>/dev/null || {
        echo "패치 실패. 새 LoadBalancer 서비스 생성..."
        
        # 기존 서비스 백업 및 삭제
        kubectl get svc loki -n monitoring -o yaml > loki-service-backup.yaml
        kubectl delete svc loki -n monitoring
        
        # 새 LoadBalancer 서비스 생성
        cat << EOF | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: loki
  namespace: monitoring
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-type: nlb
    service.beta.kubernetes.io/aws-load-balancer-scheme: internet-facing
spec:
  type: LoadBalancer
  ports:
  - name: http
    port: 3100
    targetPort: 3100
    protocol: TCP
  selector:
    app.kubernetes.io/name: loki
EOF
    }
    
    # LoadBalancer 주소 할당 대기
    echo "LoadBalancer 주소 할당 대기 중..."
    timeout=300
    counter=0
    while [ $counter -lt $timeout ]; do
        EXTERNAL_IP=$(kubectl get svc loki -n monitoring -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null)
        if [ ! -z "$EXTERNAL_IP" ] && [ "$EXTERNAL_IP" != "null" ]; then
            echo "LoadBalancer 주소: $EXTERNAL_IP"
            break
        fi
        echo "대기 중... ($counter/$timeout)"
        sleep 10
        counter=$((counter + 10))
    done
fi

echo -e "\n6. 최종 서비스 상태"
kubectl get svc loki -n monitoring

echo -e "\n7. LoadBalancer 연결 테스트"
LOKI_LB=$(kubectl get svc loki -n monitoring -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
if [ ! -z "$LOKI_LB" ] && [ "$LOKI_LB" != "null" ]; then
    LOKI_ENDPOINT="http://${LOKI_LB}:3100"
    echo "새로운 Loki 엔드포인트: $LOKI_ENDPOINT"
    
    # 연결 테스트
    echo "연결 테스트 중... (60초 대기)"
    sleep 60
    
    for i in {1..5}; do
        echo "테스트 $i/5..."
        if curl -s --max-time 10 "${LOKI_ENDPOINT}/ready" 2>/dev/null; then
            echo "✅ Loki 연결 성공!"
            
            # Lambda 환경변수 업데이트
            echo "Lambda 환경변수 업데이트 중..."
            aws lambda update-function-configuration \
              --function-name kinesis-to-loki \
              --environment Variables="{LOKI_ENDPOINT=${LOKI_ENDPOINT}}" \
              --region ap-northeast-2
            
            echo "✅ 설정 완료!"
            echo "새로운 Loki URL: $LOKI_ENDPOINT"
            break
        else
            echo "연결 실패, 재시도..."
            sleep 15
        fi
    done
else
    echo "❌ LoadBalancer 주소를 가져올 수 없습니다."
    echo "수동 확인: kubectl get svc loki -n monitoring"
fi

echo -e "\n8. Pod 직접 연결 테스트 (포트 포워딩)"
echo "LoadBalancer가 작동하지 않으면 포트 포워딩을 사용하세요:"
echo "kubectl port-forward -n monitoring pod/loki-0 3100:3100"
echo "그리고 Lambda를 워커 노드 IP로 설정:"

NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="ExternalIP")].address}')
if [ -z "$NODE_IP" ]; then
    NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
fi

if [ ! -z "$NODE_IP" ]; then
    echo "워커 노드 IP: $NODE_IP"
    echo "NodePort 서비스 생성 후 http://$NODE_IP:PORT 사용 가능"
fi

echo -e "\n=== 요약 ==="
echo "Loki Pod: $(kubectl get pod loki-0 -n monitoring -o jsonpath='{.status.phase}') (monitoring 네임스페이스)"
echo "Loki 서비스: $(kubectl get svc loki -n monitoring -o jsonpath='{.spec.type}' 2>/dev/null || echo '없음')"
echo "LoadBalancer: ${LOKI_LB:-'할당 대기 중'}"
