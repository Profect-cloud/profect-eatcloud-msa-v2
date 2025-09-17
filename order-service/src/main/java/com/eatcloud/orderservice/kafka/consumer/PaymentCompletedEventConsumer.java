package com.eatcloud.orderservice.kafka.consumer;

import com.eatcloud.logging.kafka.KafkaConsumerLoggingUtil;
import com.eatcloud.orderservice.event.PaymentCompletedEvent;
import com.eatcloud.orderservice.service.AsyncOrderCompletionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCompletedEventConsumer {

    private final AsyncOrderCompletionService asyncOrderCompletionService;

    @KafkaListener(topics = "payment.completed", groupId = "order-service-completion")
    public void handlePaymentCompleted(ConsumerRecord<String, PaymentCompletedEvent> record) {
        
        try {
            // ⭐ Kafka 헤더에서 MDC 설정
            KafkaConsumerLoggingUtil.setupMDCFromKafkaHeaders(record);
            
            PaymentCompletedEvent event = record.value();
            log.info("PaymentCompletedEvent 수신: orderId={}, customerId={}, paymentId={}", 
                    event.getOrderId(), event.getCustomerId(), event.getPaymentId());
            
            asyncOrderCompletionService.processOrderCompletion(event);
            
            // ⭐ 성공 로깅
            KafkaConsumerLoggingUtil.logKafkaConsumerEnd(record, true, null);
            
        } catch (Exception e) {
            log.error("결제 완료 처리 중 오류 발생: orderId={}, paymentId={}", 
                    record.value().getOrderId(), record.value().getPaymentId(), e);
            
            // ⭐ 실패 로깅
            KafkaConsumerLoggingUtil.logKafkaConsumerEnd(record, false, e);
            throw e;
            
        } finally {
            // ⭐ MDC 정리
            KafkaConsumerLoggingUtil.clearKafkaMDC();
        }
    }
}
