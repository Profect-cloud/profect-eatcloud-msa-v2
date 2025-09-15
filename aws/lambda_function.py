import json
import base64
import requests
import gzip
from datetime import datetime
import os
import logging
import boto3

# 로깅 설정
logger = logging.getLogger()
logger.setLevel(logging.INFO)

# Loki 엔드포인트 - 환경 변수로 설정
LOKI_ENDPOINT = os.environ.get('LOKI_ENDPOINT', 'http://localhost:3100')
ENABLE_S3_BACKUP = os.environ.get('ENABLE_S3_BACKUP', 'false').lower() == 'true'
S3_BUCKET = os.environ.get('S3_BUCKET', '')

# S3 클라이언트 (백업용)
s3 = boto3.client('s3') if ENABLE_S3_BACKUP else None

def lambda_handler(event, context):
    """
    Kinesis Data Streams에서 로그를 받아 Loki로 직접 전송
    선택적으로 S3 백업
    """
    logger.info(f"Processing {len(event['Records'])} records")
    
    success_count = 0
    error_count = 0
    batch_failures = []
    all_logs = []
    
    for record in event['Records']:
        sequence_number = record['kinesis']['sequenceNumber']
        
        try:
            # Kinesis 데이터 디코딩
            payload = base64.b64decode(record['kinesis']['data'])
            
            # gzip 압축 해제 시도
            try:
                payload = gzip.decompress(payload)
            except:
                pass  # 압축되지 않은 데이터
            
            # JSON 파싱
            if isinstance(payload, bytes):
                payload = payload.decode('utf-8')
            
            log_data = json.loads(payload)
            
            # Stateful 로그 체크 (추천 시스템용)
            is_user_action = check_if_user_action(log_data)
            
            # Loki 형식으로 변환
            loki_payload = format_for_loki(log_data, is_user_action)
            
            if loki_payload:
                # Loki로 전송
                response = send_to_loki(loki_payload)
                
                if response.status_code in [204, 200]:
                    success_count += 1
                    logger.debug(f"Successfully sent to Loki: {sequence_number}")
                else:
                    error_count += 1
                    logger.error(f"Loki returned {response.status_code}: {response.text}")
                    batch_failures.append({"itemIdentifier": sequence_number})
            
            # S3 백업 (옵션)
            if ENABLE_S3_BACKUP:
                all_logs.append(log_data)
                
        except Exception as e:
            error_count += 1
            logger.error(f"Failed to process record {sequence_number}: {str(e)}")
            batch_failures.append({"itemIdentifier": sequence_number})
    
    # S3 백업 (배치로 처리)
    if ENABLE_S3_BACKUP and all_logs:
        backup_to_s3(all_logs)
    
    logger.info(f"Processing complete - Success: {success_count}, Failures: {error_count}")
    
    return {
        'statusCode': 200,
        'batchItemFailures': batch_failures
    }

def check_if_user_action(log_data):
    """
    사용자 행동 로그인지 확인
    (추천 시스템에서 사용하는 stateful 로그)
    """
    # 사용자 행동 패턴 체크
    user_action_keywords = [
        'user_click',
        'search_query', 
        'view_cafe',
        'add_favorite',
        'write_review'
    ]
    
    log_message = log_data.get('log', '').lower()
    return any(keyword in log_message for keyword in user_action_keywords)

def format_for_loki(log_data, is_user_action=False):
    """
    로그를 Loki 형식으로 변환
    """
    # 타임스탬프 처리
    timestamp = str(int(datetime.now().timestamp() * 1e9))
    if '@timestamp' in log_data:
        try:
            dt = datetime.fromisoformat(log_data['@timestamp'].replace('Z', '+00:00'))
            timestamp = str(int(dt.timestamp() * 1e9))
        except:
            pass
    
    # Kubernetes 메타데이터
    kubernetes = log_data.get('kubernetes', {})
    
    # 레이블 구성
    labels = {
        "job": "eks-logs",
        "cluster": "eatcloud",
        "namespace": kubernetes.get('namespace_name', 'unknown'),
        "pod": kubernetes.get('pod_name', 'unknown'),
        "container": kubernetes.get('container_name', 'unknown'),
        "node": kubernetes.get('host', 'unknown')
    }
    
    # Stateful 로그 표시
    if is_user_action:
        labels["log_type"] = "user_action"
        labels["stateful"] = "true"
    else:
        labels["log_type"] = "system"
        labels["stateful"] = "false"
    
    # 로그 레벨 추출
    log_message = log_data.get('log', '')
    if 'ERROR' in log_message.upper():
        labels["level"] = "error"
    elif 'WARN' in log_message.upper():
        labels["level"] = "warn"
    elif 'INFO' in log_message.upper():
        labels["level"] = "info"
    else:
        labels["level"] = "debug"
    
    # 빈 로그 스킵
    if not log_message.strip():
        return None
    
    return {
        "streams": [
            {
                "stream": labels,
                "values": [[timestamp, log_message]]
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
        "X-Scope-OrgID": "1"
    }
    
    try:
        response = requests.post(
            url,
            json=payload,
            headers=headers,
            timeout=5
        )
        return response
        
    except requests.exceptions.Timeout:
        logger.error("Timeout connecting to Loki")
        raise
    except Exception as e:
        logger.error(f"Error sending to Loki: {str(e)}")
        raise

def backup_to_s3(logs):
    """
    S3에 로그 백업 (선택적)
    """
    if not s3 or not S3_BUCKET:
        return
    
    try:
        # 현재 시간으로 파일명 생성
        now = datetime.now()
        key = f"logs/year={now.year}/month={now.month:02d}/day={now.day:02d}/hour={now.hour:02d}/{now.timestamp()}.json.gz"
        
        # JSON으로 변환 후 압축
        data = '\n'.join(json.dumps(log) for log in logs)
        compressed = gzip.compress(data.encode('utf-8'))
        
        # S3 업로드
        s3.put_object(
            Bucket=S3_BUCKET,
            Key=key,
            Body=compressed,
            ContentType='application/json',
            ContentEncoding='gzip'
        )
        
        logger.info(f"Backed up {len(logs)} logs to s3://{S3_BUCKET}/{key}")
        
    except Exception as e:
        logger.error(f"Failed to backup to S3: {str(e)}")
        # S3 백업 실패는 전체 처리를 실패시키지 않음
