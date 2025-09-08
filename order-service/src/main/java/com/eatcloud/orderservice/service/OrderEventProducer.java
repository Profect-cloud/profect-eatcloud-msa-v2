package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.event.OrderCreatedEvent;
import com.eatcloud.orderservice.event.OrderCancelledEvent;
import com.eatcloud.orderservice.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

	private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String ORDER_CREATED_TOPIC = "order.created";
    private static final String ORDER_CANCELLED_TOPIC = "order.cancelled";
    private static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";

	public CompletableFuture<SendResult<String, Object>> publishOrderCreated(OrderCreatedEvent event) {
		log.info("주문 생성 이벤트 발행: orderId={}, customerId={}", event.getOrderId(), event.getCustomerId());

		return kafkaTemplate.send(ORDER_CREATED_TOPIC, event.getOrderId().toString(), event)
				.whenComplete((result, throwable) -> {
					if (throwable != null) {
						log.error("주문 생성 이벤트 발행 실패 - DLQ 처리 필요: orderId={}", event.getOrderId(), throwable);
						// Producer 실패는 Kafka 내부 재시도 후 최종 실패 시 여기 도달
						sendToEventDLQ("order.created", event.getOrderId().toString(), event, throwable);
					} else {
						log.info("주문 생성 이벤트 발행 성공: orderId={}, partition={}, offset={}",
								event.getOrderId(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
					}
				});
	}

	public CompletableFuture<SendResult<String, Object>> publishOrderCancelled(OrderCancelledEvent event) {
		log.info("주문 취소 이벤트 발행: orderId={}, customerId={}, reason={}", 
				event.getOrderId(), event.getCustomerId(), event.getCancelReason());

		return kafkaTemplate.send(ORDER_CANCELLED_TOPIC, event.getOrderId().toString(), event)
				.whenComplete((result, throwable) -> {
					if (throwable != null) {
						log.error("주문 취소 이벤트 발행 실패 - DLQ 처리 필요: orderId={}", event.getOrderId(), throwable);
						sendToEventDLQ("order.cancelled", event.getOrderId().toString(), event, throwable);
					} else {
						log.info("주문 취소 이벤트 발행 성공: orderId={}, partition={}, offset={}",
								event.getOrderId(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
					}
				});
	}

	/**
	 * 결제 완료 이벤트 발행 (안전한 비동기 후처리 시작)
	 */
	public CompletableFuture<SendResult<String, Object>> publishPaymentCompleted(PaymentCompletedEvent event) {
		log.info("결제 완료 이벤트 발행: orderId={}, paymentId={}, customerId={}", 
				event.getOrderId(), event.getPaymentId(), event.getCustomerId());

		return kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC, event.getOrderId().toString(), event)
				.whenComplete((result, throwable) -> {
					if (throwable != null) {
						log.error("결제 완료 이벤트 발행 실패 - DLQ 처리 필요: orderId={}, paymentId={}", 
								event.getOrderId(), event.getPaymentId(), throwable);
						sendToEventDLQ(PAYMENT_COMPLETED_TOPIC, event.getOrderId().toString(), event, throwable);
					} else {
						log.info("결제 완료 이벤트 발행 성공: orderId={}, paymentId={}, partition={}, offset={}",
								event.getOrderId(), event.getPaymentId(), 
								result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
					}
				});
	}

	/**
	 * 이벤트 발행 실패 시 DLQ로 전송
	 */
	private void sendToEventDLQ(String originalTopic, String key, Object event, Throwable error) {
		try {
			String dlqTopic = originalTopic + ".producer.DLQ";
			String errorMessage = String.format(
				"이벤트 발행 실패 - 원본 토픽: %s, 키: %s, 이벤트: %s, 에러: %s",
				originalTopic, key, event.toString(), error.getMessage()
			);

			kafkaTemplate.send(dlqTopic, key, errorMessage);
			log.info("이벤트 발행 실패를 DLQ로 전송 완료: topic={}, key={}", dlqTopic, key);
			
		} catch (Exception dlqException) {
			log.error("DLQ 전송도 실패 - 수동 처리 필요: topic={}, key={}, event={}", 
					originalTopic, key, event, dlqException);
		}
	}
}