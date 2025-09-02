package com.eatcloud.paymentservice.service;

import com.eatcloud.paymentservice.event.PaymentCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

	private final KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate;
	private static final String PAYMENT_CREATED_TOPIC = "payment.created";

	public CompletableFuture<SendResult<String, PaymentCreatedEvent>> publishPaymentCreated(PaymentCreatedEvent event) {
		log.info("결제 생성 이벤트 발행: paymentId={}, orderId={}", event.getPaymentId(), event.getOrderId());

		return kafkaTemplate.send(PAYMENT_CREATED_TOPIC, event.getPaymentId().toString(), event)
				.whenComplete((result, throwable) -> {
					if (throwable != null) {
						log.error("결제 생성 이벤트 발행 실패: paymentId={}", event.getPaymentId(), throwable);
					} else {
						log.info("결제 생성 이벤트 발행 성공: paymentId={}, partition={}, offset={}",
								event.getPaymentId(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
					}
				});
	}
}