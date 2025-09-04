package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.event.OrderCreatedEvent;
import com.eatcloud.orderservice.event.OrderCancelledEvent;
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

	public CompletableFuture<SendResult<String, Object>> publishOrderCreated(OrderCreatedEvent event) {
		log.info("주문 생성 이벤트 발행: orderId={}, customerId={}", event.getOrderId(), event.getCustomerId());

		return kafkaTemplate.send(ORDER_CREATED_TOPIC, event.getOrderId().toString(), event)
				.whenComplete((result, throwable) -> {
					if (throwable != null) {
						log.error("주문 생성 이벤트 발행 실패: orderId={}", event.getOrderId(), throwable);
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
						log.error("주문 취소 이벤트 발행 실패: orderId={}", event.getOrderId(), throwable);
					} else {
						log.info("주문 취소 이벤트 발행 성공: orderId={}, partition={}, offset={}",
								event.getOrderId(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
					}
				});
	}
}