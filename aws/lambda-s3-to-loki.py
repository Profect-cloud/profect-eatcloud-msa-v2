import json
import boto3
import gzip
import requests
from datetime import datetime
import os

s3 = boto3.client('s3')
LOKI_ENDPOINT = os.environ.get('LOKI_ENDPOINT', 'http://loki.monitoring.svc.cluster.local:3100')

def lambda_handler(event, context):
    """
    S3에 저장된 로그를 Loki로 전송
    Kinesis Firehose가 S3에 파일을 저장하면 트리거됨
    """
    
    for record in event['Records']:
        bucket = record['s3']['bucket']['name']
        key = record['s3']['object']['key']
        
        print(f"Processing s3://{bucket}/{key}")
        
        # S3에서 파일 다운로드
        obj = s3.get_object(Bucket=bucket, Key=key)
        
        # GZIP 압축 해제
        if key.endswith('.gz'):
            content = gzip.decompress(obj['Body'].read())
        else:
            content = obj['Body'].read()
        
        # 줄 단위로 처리
        lines = content.decode('utf-8').strip().split('\n')
        
        # Loki 형식으로 변환
        streams = []
        for line in lines:
            try:
                log_data = json.loads(line)
                
                # Kubernetes 메타데이터 추출
                kubernetes = log_data.get('kubernetes', {})
                
                labels = {
                    "job": "s3-logs",
                    "namespace": kubernetes.get('namespace_name', 'unknown'),
                    "pod": kubernetes.get('pod_name', 'unknown'),
                    "container": kubernetes.get('container_name', 'unknown')
                }
                
                # 타임스탬프 처리
                timestamp = str(int(datetime.now().timestamp() * 1e9))
                if '@timestamp' in log_data:
                    try:
                        dt = datetime.fromisoformat(log_data['@timestamp'].replace('Z', '+00:00'))
                        timestamp = str(int(dt.timestamp() * 1e9))
                    except:
                        pass
                
                # 로그 메시지
                log_message = log_data.get('log', log_data.get('message', ''))
                
                if log_message:
                    streams.append({
                        "stream": labels,
                        "values": [[timestamp, log_message]]
                    })
                    
            except json.JSONDecodeError:
                continue
        
        # Loki로 전송
        if streams:
            payload = {"streams": streams}
            
            try:
                response = requests.post(
                    f"{LOKI_ENDPOINT}/loki/api/v1/push",
                    json=payload,
                    headers={"Content-Type": "application/json"},
                    timeout=10
                )
                
                if response.status_code in [200, 204]:
                    print(f"Successfully sent {len(streams)} logs to Loki")
                else:
                    print(f"Failed to send logs: {response.status_code} - {response.text}")
                    
            except Exception as e:
                print(f"Error sending to Loki: {str(e)}")
    
    return {
        'statusCode': 200,
        'body': json.dumps('Processing complete')
    }
