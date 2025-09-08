package com.eatcloud.logging.kafka;

import com.eatcloud.logging.mdc.MDCUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;

@Slf4j
public class KafkaConsumerLoggingUtil {

    /**
     * Kafka 메시지 헤더에서 MDC 정보를 추출하여 설정
     */
    public static void setupMDCFromKafkaHeaders(ConsumerRecord<String, ?> record) {
        Headers headers = record.headers();
        
        // Request ID 추출
        Header requestIdHeader = headers.lastHeader("X-Request-ID");
        if (requestIdHeader != null) {
            String requestId = new String(requestIdHeader.value(), StandardCharsets.UTF_8);
            MDCUtil.setRequestId(requestId);
        } else {
            // 새로운 Request ID 생성
            MDCUtil.setRequestId(MDCUtil.generateRequestId());
        }
        
        // User ID 추출
        Header userIdHeader = headers.lastHeader("X-User-ID");
        if (userIdHeader != null) {
            String userId = new String(userIdHeader.value(), StandardCharsets.UTF_8);
            MDCUtil.setUserId(userId);
        }
        
        // Source Service 추출
        Header sourceServiceHeader = headers.lastHeader("X-Source-Service");
        if (sourceServiceHeader != null) {
            String sourceService = new String(sourceServiceHeader.value(), StandardCharsets.UTF_8);
            log.debug("KAFKA CONSUMER - Message from service: {}", sourceService);
        }
        
        // 소비 시작 시간 설정
        MDCUtil.setRequestStartTime(System.currentTimeMillis());
        
        log.info("KAFKA CONSUMER START - Topic: {}, Partition: {}, Offset: {}, Key: {}", 
                record.topic(), record.partition(), record.offset(), record.key());
    }
    
    /**
     * Kafka 메시지 처리 완료 로깅
     */
    public static void logKafkaConsumerEnd(ConsumerRecord<String, ?> record, boolean success, Exception exception) {
        long duration = System.currentTimeMillis() - 
                Long.parseLong(MDCUtil.getRequestId() != null ? 
                        MDCUtil.getRequestId() : String.valueOf(System.currentTimeMillis()));
        
        if (success) {
            log.info("KAFKA CONSUMER SUCCESS - Topic: {}, Partition: {}, Offset: {}, Duration: {}ms", 
                    record.topic(), record.partition(), record.offset(), duration);
        } else {
            log.error("KAFKA CONSUMER ERROR - Topic: {}, Partition: {}, Offset: {}, Duration: {}ms, Error: {}", 
                    record.topic(), record.partition(), record.offset(), duration, 
                    exception != null ? exception.getMessage() : "Unknown error");
        }
    }
    
    /**
     * Kafka 메시지 처리를 위한 MDC 정리
     */
    public static void clearKafkaMDC() {
        MDCUtil.clear();
    }
}
