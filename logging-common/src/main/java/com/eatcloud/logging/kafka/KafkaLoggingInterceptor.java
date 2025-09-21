package com.eatcloud.logging.kafka;

import com.eatcloud.logging.mdc.MDCUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;

import java.util.Map;

@Slf4j
public class KafkaLoggingInterceptor implements ProducerInterceptor<String, Object> {

    @Override
    public ProducerRecord<String, Object> onSend(ProducerRecord<String, Object> record) {
        // MDC 정보를 Kafka 헤더에 추가
        Headers headers = record.headers();
        
        String requestId = MDCUtil.getRequestId();
        if (requestId != null) {
            headers.add("X-Request-ID", requestId.getBytes());
        }
        
        String userId = MDCUtil.getUserId();
        if (userId != null) {
            headers.add("X-User-ID", userId.getBytes());
        }
        
        String serviceName = MDCUtil.getServiceName();
        if (serviceName != null) {
            headers.add("X-Source-Service", serviceName.getBytes());
        }
        
        log.debug("KAFKA PRODUCER - Topic: {}, Key: {}, RequestId: {}, UserId: {}", 
                record.topic(), record.key(), requestId, userId);
        
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        if (exception != null) {
            log.error("KAFKA PRODUCER ERROR - Topic: {}, Partition: {}, Offset: {}, Error: {}", 
                    metadata.topic(), metadata.partition(), metadata.offset(), exception.getMessage());
        } else {
            log.debug("KAFKA PRODUCER SUCCESS - Topic: {}, Partition: {}, Offset: {}", 
                    metadata.topic(), metadata.partition(), metadata.offset());
        }
    }

    @Override
    public void close() {
        // cleanup if needed
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // configuration if needed
    }
}
