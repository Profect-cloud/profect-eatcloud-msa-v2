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

# Loki 엔드포인트
LOKI_ENDPOINT = os.environ.get('LOKI_ENDPOINT', 'http://localhost:3100')
LOG_TYPE = os.environ.get('LOG_TYPE', 'stateless')  # stateless or stateful

def lambda_handler(event, context):
    """
    Kinesis에서 stateless/stateful 로그를 받아 Loki로 전송
    """
    logger.info(f"Processing {len(event['Records'])} {LOG_TYPE} logs")
    
    success_count = 0
    error_count = 0
    batch_failures = []
    
    # 배치 처리를 위한 스트림 수집
    loki_streams = []
    
    for record in event['Records']:
        sequence_number = record['kinesis']['sequenceNumber']
        
        try:
            # Kinesis 데이터 디코딩
            payload = base64.b64decode(record['kinesis']['data'])
            
            # gzip 압축 해제 시도
            try:
                payload = gzip.decompress(payload)
            except:
                pass
            
            # JSON 파싱
            if isinstance(payload, bytes):
                payload = payload.decode('utf-8')
            
            log_data = json.loads(payload)
            
            # Loki 스트림 생성
            stream = create_loki_stream(log_data)
            if stream:
                loki_streams.append(stream)
                success_count += 1
                
        except Exception as e:
            error_count += 1
            logger.error(f"Failed to process record {sequence_number}: {str(e)}")
            batch_failures.append({"itemIdentifier": sequence_number})
    
    # Loki로 배치 전송
    if loki_streams:
        try:
            send_batch_to_loki(loki_streams)
            logger.info(f"Sent {len(loki_streams)} logs to Loki")
        except Exception as e:
            logger.error(f"Failed to send to Loki: {str(e)}")
            # 전체 배치 실패 처리
            return {
                'statusCode': 500,
                'batchItemFailures': [{"itemIdentifier": r['kinesis']['sequenceNumber']} 
                                     for r in event['Records']]
            }
    
    logger.info(f"Processing complete - Success: {success_count}, Failures: {error_count}")
    
    return {
        'statusCode': 200,
        'batchItemFailures': batch_failures
    }

def create_loki_stream(log_data):
    """
    로그 데이터를 Loki 스트림 형식으로 변환
    """
    # 타임스탬프 처리
    timestamp = log_data.get('@timestamp', '')
    if timestamp:
        try:
            dt = datetime.fromisoformat(timestamp.replace('Z', '+00:00'))
            timestamp_ns = str(int(dt.timestamp() * 1e9))
        except:
            timestamp_ns = str(int(datetime.now().timestamp() * 1e9))
    else:
        timestamp_ns = str(int(datetime.now().timestamp() * 1e9))
    
    # 레이블 구성
    labels = {
        "job": "eatcloud-logs",
        "log_type": LOG_TYPE,
        "cluster_name": log_data.get('cluster_name', 'eatcloud'),
        "region": log_data.get('region', 'ap-northeast-2')
    }
    
    # 추가 메타데이터가 있으면 레이블로 추가
    if 'service' in log_data:
        labels['service'] = log_data['service']
    if 'namespace' in log_data:
        labels['namespace'] = log_data['namespace']
    if 'pod' in log_data:
        labels['pod'] = log_data['pod']
    
    # 로그 레벨 감지
    message = log_data.get('message', log_data.get('log', ''))
    if 'ERROR' in message.upper():
        labels['level'] = 'error'
    elif 'WARN' in message.upper():
        labels['level'] = 'warn'
    elif 'INFO' in message.upper():
        labels['level'] = 'info'
    else:
        labels['level'] = 'debug'
    
    # stateful 로그의 경우 추가 정보
    if LOG_TYPE == 'stateful':
        if 'sessionId' in log_data:
            labels['session_id'] = log_data['sessionId']
        if 'userId' in log_data:
            labels['user_id'] = log_data['userId']
        if 'transactionId' in log_data:
            labels['transaction_id'] = log_data['transactionId']
    
    # 빈 메시지 스킵
    if not message:
        return None
    
    return {
        "stream": labels,
        "values": [[timestamp_ns, json.dumps(log_data)]]  # 전체 JSON 저장
    }

def send_batch_to_loki(streams):
    """
    여러 스트림을 Loki로 배치 전송
    """
    # 스트림별로 그룹화
    grouped_streams = {}
    for stream_data in streams:
        stream_key = json.dumps(stream_data['stream'], sort_keys=True)
        if stream_key not in grouped_streams:
            grouped_streams[stream_key] = {
                "stream": stream_data['stream'],
                "values": []
            }
        grouped_streams[stream_key]['values'].extend(stream_data['values'])
    
    # Loki payload 구성
    payload = {
        "streams": list(grouped_streams.values())
    }
    
    # Loki로 전송
    url = f"{LOKI_ENDPOINT}/loki/api/v1/push"
    headers = {
        "Content-Type": "application/json",
        "X-Scope-OrgID": "1"
    }
    
    response = requests.post(
        url,
        json=payload,
        headers=headers,
        timeout=10
    )
    
    if response.status_code not in [200, 204]:
        raise Exception(f"Loki returned {response.status_code}: {response.text}")
    
    return response
