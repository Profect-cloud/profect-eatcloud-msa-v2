// package: com.eatcloud.storeservice.config
package com.eatcloud.storeservice.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.*;

@Configuration
public class KafkaStockStringConfig {

    @Bean
    public ConsumerFactory<String, String> stockStringConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap,
            @Value("${spring.kafka.consumer.group-id:store-service}") String groupId
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-projector");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> stockStringKafkaListenerContainerFactory(
            ConsumerFactory<String, String> stockStringConsumerFactory
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(stockStringConsumerFactory);

        // ✅ MANUAL ack 모드 (ack.acknowledge()를 직접 호출할 수 있게)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // ✅ 에러 시 재시도 없이 스킵하고 오프셋 커밋(무한 재시도 방지)
        var eh = new DefaultErrorHandler(
                (record, ex) -> {
                    // 로그만 남기고 스킵
                    // log.error("[Projector] skip: topic={} offset={} err={}", record.topic(), record.offset(), ex.toString());
                },
                new FixedBackOff(0L, 0L) // 재시도 0회
        );
        eh.setCommitRecovered(true);
        factory.setCommonErrorHandler(eh);

        factory.setConcurrency(2);
        return factory;
    }

}
