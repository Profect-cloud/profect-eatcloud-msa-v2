-- ========================================
-- Kinesis Analytics SQL 쿼리 예시
-- ========================================

-- 1. 에러 로그 실시간 감지 및 알림
CREATE OR REPLACE STREAM "ERROR_ALERT_STREAM" (
    namespace VARCHAR(256),
    pod_name VARCHAR(256),
    error_message VARCHAR(8192),
    error_count INTEGER,
    alert_time TIMESTAMP
);

CREATE OR REPLACE PUMP "ERROR_ALERT_PUMP" AS 
INSERT INTO "ERROR_ALERT_STREAM"
SELECT STREAM
    kubernetes_namespace,
    kubernetes_pod_name,
    SUBSTRING("log", 1, 500) as error_message,
    COUNT(*) OVER (
        PARTITION BY kubernetes_namespace, kubernetes_pod_name 
        RANGE INTERVAL '1' MINUTE PRECEDING
    ) as error_count,
    CURRENT_TIMESTAMP
FROM "TEMP_STREAM"
WHERE log_level = 'ERROR'
    AND COUNT(*) OVER (
        PARTITION BY kubernetes_namespace, kubernetes_pod_name 
        RANGE INTERVAL '1' MINUTE PRECEDING
    ) > 10; -- 1분에 10개 이상 에러 발생 시

-- 2. 로그 볼륨 분석 (네임스페이스별)
CREATE OR REPLACE STREAM "LOG_VOLUME_STREAM" (
    namespace VARCHAR(256),
    log_count INTEGER,
    avg_log_size INTEGER,
    max_log_size INTEGER,
    window_time TIMESTAMP
);

CREATE OR REPLACE PUMP "LOG_VOLUME_PUMP" AS 
INSERT INTO "LOG_VOLUME_STREAM"
SELECT STREAM
    kubernetes_namespace,
    COUNT(*) as log_count,
    AVG(LENGTH("log")) as avg_log_size,
    MAX(LENGTH("log")) as max_log_size,
    ROWTIME
FROM "TEMP_STREAM"
GROUP BY kubernetes_namespace, 
         ROWTIME RANGE INTERVAL '5' MINUTE;

-- 3. 특정 패턴 감지 (예: SQL Injection 시도)
CREATE OR REPLACE STREAM "SECURITY_ALERT_STREAM" (
    namespace VARCHAR(256),
    pod_name VARCHAR(256),
    suspicious_pattern VARCHAR(256),
    log_sample VARCHAR(500),
    detection_time TIMESTAMP
);

CREATE OR REPLACE PUMP "SECURITY_PUMP" AS 
INSERT INTO "SECURITY_ALERT_STREAM"
SELECT STREAM
    kubernetes_namespace,
    kubernetes_pod_name,
    CASE
        WHEN UPPER("log") LIKE '%DROP TABLE%' THEN 'SQL_INJECTION_DROP'
        WHEN UPPER("log") LIKE '%UNION SELECT%' THEN 'SQL_INJECTION_UNION'
        WHEN "log" LIKE '%<script>%' THEN 'XSS_ATTEMPT'
        WHEN "log" LIKE '%../../%' THEN 'PATH_TRAVERSAL'
        ELSE 'UNKNOWN'
    END as suspicious_pattern,
    SUBSTRING("log", 1, 500) as log_sample,
    CURRENT_TIMESTAMP
FROM "TEMP_STREAM"
WHERE UPPER("log") LIKE '%DROP TABLE%'
   OR UPPER("log") LIKE '%UNION SELECT%'
   OR "log" LIKE '%<script>%'
   OR "log" LIKE '%../../%';

-- 4. 응답 시간 분석 (API 로그에서)
CREATE OR REPLACE STREAM "RESPONSE_TIME_STREAM" (
    endpoint VARCHAR(256),
    avg_response_time DOUBLE,
    p95_response_time DOUBLE,
    request_count INTEGER,
    window_time TIMESTAMP
);

CREATE OR REPLACE PUMP "RESPONSE_TIME_PUMP" AS 
INSERT INTO "RESPONSE_TIME_STREAM"
SELECT STREAM
    REGEXP_EXTRACT("log", '(GET|POST|PUT|DELETE)\s+([^\s]+)', 2) as endpoint,
    AVG(CAST(REGEXP_EXTRACT("log", 'took=([0-9]+)ms', 1) AS DOUBLE)) as avg_response_time,
    APPROXIMATE_PERCENTILE(
        CAST(REGEXP_EXTRACT("log", 'took=([0-9]+)ms', 1) AS DOUBLE), 
        0.95
    ) as p95_response_time,
    COUNT(*) as request_count,
    ROWTIME
FROM "TEMP_STREAM"
WHERE "log" LIKE '%took=%ms%'
GROUP BY REGEXP_EXTRACT("log", '(GET|POST|PUT|DELETE)\s+([^\s]+)', 2),
         ROWTIME RANGE INTERVAL '5' MINUTE;

-- 5. 이상 패턴 감지 (Anomaly Detection)
CREATE OR REPLACE STREAM "ANOMALY_STREAM" (
    namespace VARCHAR(256),
    metric_name VARCHAR(256),
    current_value DOUBLE,
    avg_value DOUBLE,
    std_dev DOUBLE,
    z_score DOUBLE,
    is_anomaly BOOLEAN
);

CREATE OR REPLACE PUMP "ANOMALY_PUMP" AS 
INSERT INTO "ANOMALY_STREAM"
SELECT STREAM
    kubernetes_namespace,
    'log_rate' as metric_name,
    current_count as current_value,
    avg_count as avg_value,
    std_count as std_dev,
    (current_count - avg_count) / NULLIF(std_count, 0) as z_score,
    ABS((current_count - avg_count) / NULLIF(std_count, 0)) > 3 as is_anomaly
FROM (
    SELECT STREAM
        kubernetes_namespace,
        COUNT(*) as current_count,
        AVG(COUNT(*)) OVER (
            PARTITION BY kubernetes_namespace 
            RANGE INTERVAL '1' HOUR PRECEDING
        ) as avg_count,
        STDDEV_POP(COUNT(*)) OVER (
            PARTITION BY kubernetes_namespace 
            RANGE INTERVAL '1' HOUR PRECEDING
        ) as std_count
    FROM "TEMP_STREAM"
    GROUP BY kubernetes_namespace, 
             ROWTIME RANGE INTERVAL '1' MINUTE
);

-- 6. 처리된 로그를 최종 출력 스트림으로
CREATE OR REPLACE STREAM "FINAL_OUTPUT_STREAM" (
    original_log VARCHAR(8192),
    enriched_data OBJECT,
    processing_timestamp TIMESTAMP
);

CREATE OR REPLACE PUMP "FINAL_OUTPUT_PUMP" AS 
INSERT INTO "FINAL_OUTPUT_STREAM"
SELECT STREAM
    "log" as original_log,
    CURSOR(
        SELECT STREAM 
            kubernetes_namespace as "namespace",
            kubernetes_pod_name as "pod",
            kubernetes_container_name as "container",
            log_level as "level",
            CASE 
                WHEN log_level = 'ERROR' THEN true 
                ELSE false 
            END as "requires_attention"
        FROM "TEMP_STREAM"
    ) as enriched_data,
    CURRENT_TIMESTAMP as processing_timestamp
FROM "TEMP_STREAM";
