import json
import base64
import requests
import gzip
from datetime import datetime
import os
import logging

# 로깅 설정
logger = logging.getLogger()
logger.setLevel(logging.INFO)

# Loki 엔드포인트 - 환경 변수로 설정
LOKI_ENDPOINT = os.environ.get('LOKI_ENDPOINT', 'http://localhost:3100')

def lambda_handler(event, context):
    """
    Kinesis Data Streams에서 로그를 받아 Loki로 전송
    """
    logger.info(f"Processing {len(event['Records'])} records")
    logger.info(f"Loki endpoint: {LOKI_ENDPOINT}")
    
    success_count = 0
    error_count = 0
    batch_failures = []
    
    for record in event['Records']:
        sequence_number = record['kinesis']['sequenceNumber']
        
        try:
            # Kinesis 데이터 디코딩
            payload = base64.b64decode(record['kinesis']['data'])
            
            # gzip 압축 해제 시도
            try:
                payload = gzip.decompress(payload)
            except:
                pass  # 압축되지 않은 데이터일 수 있음
            
            # JSON 파싱
            if isinstance(payload, bytes):
                payload = payload.decode('utf-8')
            
            log_data = json.loads(payload)
            
            # Loki 형식으로 변환
            loki_payload = format_for_loki(log_data)
            
            # Loki로 전송
            response = send_to_loki(loki_payload)
            
            if response.status_code in [204, 200]:
                success_count += 1
                logger.debug(f"Successfully sent log to Loki: {sequence_number}")
            else:
                error_count += 1
                logger.error(f"Loki returned {response.status_code}: {response.text}")
                batch_failures.append({"itemIdentifier": sequence_number})
                
        except json.JSONDecodeError as e:
            error_count += 1
            logger.error(f"JSON decode error for record {sequence_number}: {str(e)}")
            batch_failures.append({"itemIdentifier": sequence_number})
        except Exception as e:
            error_count += 1
            logger.error(f"Failed to process record {sequence_number}: {str(e)}")
            batch_failures.append({"itemIdentifier": sequence_number})
    
    logger.info(f"Processing complete - Success: {success_count}, Failures: {error_count}")
    
    # 부분 실패 처리 - 실패한 레코드만 재시도
    return {
        'statusCode': 200,
        'batchItemFailures': batch_failures
    }

def format_for_loki(log_data):
    """
    Fluent Bit 로그를 Loki 형식으로 변환
    """
    # 타임스탬프 처리
    if '@timestamp' in log_data:
        # ISO 형식 타임스탬프 파싱
        try:
            dt = datetime.fromisoformat(log_data['@timestamp'].replace('Z', '+00:00'))
            timestamp = str(int(dt.timestamp() * 1e9))
        except:
            timestamp = str(int(datetime.now().timestamp() * 1e9))
    else:
        timestamp = str(int(datetime.now().timestamp() * 1e9))
    
    # Kubernetes 메타데이터 추출
    kubernetes = log_data.get('kubernetes', {})
    
    # 레이블 구성
    labels = {
        "job": "eks-logs",
        "cluster": "eatcloud",
        "namespace": kubernetes.get('namespace_name', kubernetes.get('namespace', 'unknown')),
        "pod": kubernetes.get('pod_name', 'unknown'),
        "container": kubernetes.get('container_name', 'unknown'),
        "node": kubernetes.get('host', kubernetes.get('node', 'unknown'))
    }
    
    # 추가 레이블
    if 'labels' in kubernetes:
        for key, value in kubernetes['labels'].items():
            # Loki 레이블 이름 규칙에 맞게 변환
            label_key = key.replace('-', '_').replace('.', '_')
            if label_key not in labels:
                labels[f"k8s_{label_key}"] = str(value)
    
    # 로그 메시지 추출
    log_message = log_data.get('log', log_data.get('message', ''))
    
    # 빈 로그는 건너뛰기
    if not log_message or log_message.strip() == '':
        logger.debug("Skipping empty log message")
        return None
    
    # Loki 스트림 형식
    return {
        "streams": [
            {
                "stream": labels,
                "values": [
                    [timestamp, log_message]
                ]
            }
        ]
    }

def send_to_loki(payload):
    """
    Loki Push API로 로그 전송
    """
    if not payload:
        return type('obj', (object,), {'status_code': 200})()
    
    url = f"{LOKI_ENDPOINT}/loki/api/v1/push"
    headers = {
        "Content-Type": "application/json",
        "X-Scope-OrgID": "1"  # 멀티테넌시를 사용하는 경우
    }
    
    try:
        response = requests.post(
            url,
            json=payload,
            headers=headers,
            timeout=5
        )
        
        logger.debug(f"Loki response: {response.status_code}")
        return response
        
    except requests.exceptions.Timeout:
        logger.error("Timeout connecting to Loki")
        raise
    except requests.exceptions.ConnectionError as e:
        logger.error(f"Connection error to Loki: {str(e)}")
        raise
    except Exception as e:
        logger.error(f"Unexpected error sending to Loki: {str(e)}")
        raise
