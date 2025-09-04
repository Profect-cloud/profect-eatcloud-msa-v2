package com.eatcloud.customerservice.service;

import com.eatcloud.customerservice.entity.Customer;
import com.eatcloud.customerservice.entity.PointReservation;
import com.eatcloud.customerservice.entity.ReservationStatus;
import com.eatcloud.customerservice.event.OrderCreatedEvent;
import com.eatcloud.customerservice.repository.CustomerRepository;
import com.eatcloud.customerservice.repository.PointReservationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {
    
    private final PointReservationService pointReservationService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(topics = "order.created", groupId = "customer-service", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handleOrderCreated(String eventJson) {
        try {
            log.info("주문 생성 이벤트 수신 (JSON): {}", eventJson);

            OrderCreatedEvent event = objectMapper.readValue(eventJson, OrderCreatedEvent.class);
            
            log.info("주문 생성 이벤트 파싱 완료: orderId={}, customerId={}, pointsToUse={}",
                    event.getOrderId(), event.getCustomerId(), event.getPointsToUse());

            if (event.getPointsToUse() == null || event.getPointsToUse() <= 0) {
                log.info("포인트 사용 없음: orderId={}", event.getOrderId());
                return;
            }

            PointReservation reservation = pointReservationService.createReservation(
                    event.getCustomerId(),
                    event.getOrderId(),
                    event.getPointsToUse()
            );

            log.info("포인트 차감 예약 완료: orderId={}, customerId={}, points={}, reservationId={}",
                    event.getOrderId(), event.getCustomerId(), event.getPointsToUse(), reservation.getReservationId());
            
            // 주문 생성과 동시에 포인트 예약을 처리 (실제 차감)
            pointReservationService.processReservation(event.getOrderId());
            log.info("포인트 예약 처리 완료: orderId={}, customerId={}", 
                    event.getOrderId(), event.getCustomerId());

        } catch (JsonProcessingException e) {
            log.error("주문 생성 이벤트 JSON 파싱 실패: eventJson={}", eventJson, e);
        } catch (Exception e) {
            log.error("주문 생성 이벤트 처리 실패: eventJson={}", eventJson, e);
            // TODO: Dead Letter Queue 구현 필요
            throw e;
        }
    }
} 
