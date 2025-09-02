package com.eatcloud.customerservice.service;

import com.eatcloud.customerservice.entity.Customer;
import com.eatcloud.customerservice.entity.PointReservation;
import com.eatcloud.customerservice.entity.ReservationStatus;
import com.eatcloud.customerservice.repository.CustomerRepository;
import com.eatcloud.customerservice.repository.PointReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointReservationService {

    private final CustomerRepository customerRepository;
    private final PointReservationRepository pointReservationRepository;
    private final RestTemplate restTemplate;

    private static final String PAYMENT_SERVICE_URL = "http://payment-service/api/v1/payments";

    @Transactional
    public PointReservation createReservation(UUID customerId, UUID orderId, Integer points) {
        log.info("포인트 예약 생성 시작: customerId={}, orderId={}, points={}", customerId, orderId, points);

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("고객을 찾을 수 없습니다: " + customerId));

        if (pointReservationRepository.existsByOrderId(orderId)) {
            throw new IllegalStateException("이미 포인트 예약이 존재합니다: orderId=" + orderId);
        }

        customer.reservePoints(points);
        customerRepository.save(customer);

        PointReservation reservation = PointReservation.builder()
                .customerId(customerId)
                .orderId(orderId)
                .points(points)
                .status(ReservationStatus.RESERVED)
                .build();

        PointReservation savedReservation = pointReservationRepository.save(reservation);

        log.info("포인트 예약 생성 완료: reservationId={}, customerId={}, orderId={}, points={}",
                savedReservation.getReservationId(), customerId, orderId, points);

        return savedReservation;
    }

    @Transactional
    public void processReservation(UUID orderId) {
        log.info("포인트 예약 처리 시작: orderId={}", orderId);

        Optional<PointReservation> reservationOpt = pointReservationRepository.findByOrderId(orderId);

        if (reservationOpt.isEmpty()) {
            log.info("포인트 예약 정보가 없음: orderId={}", orderId);
            return;
        }

        PointReservation reservation = reservationOpt.get();

        if (!reservation.canProcess()) {
            log.warn("처리할 수 없는 예약 상태: orderId={}, status={}", orderId, reservation.getStatus());
            return;
        }

        reservation.process();
        pointReservationRepository.save(reservation);

        log.info("포인트 예약 처리 완료: orderId={}, reservationId={}", orderId, reservation.getReservationId());
    }

    @Transactional
    public void cancelReservation(UUID orderId) {
        log.info("포인트 예약 취소 시작: orderId={}", orderId);

        Optional<PointReservation> reservationOpt = pointReservationRepository.findByOrderId(orderId);

        if (reservationOpt.isEmpty()) {
            log.info("포인트 예약 정보가 없음: orderId={}", orderId);
            return;
        }

        PointReservation reservation = reservationOpt.get();

        if (!reservation.canCancel()) {
            log.warn("취소할 수 없는 예약 상태: orderId={}, status={}", orderId, reservation.getStatus());
            return;
        }

        Customer customer = customerRepository.findById(reservation.getCustomerId())
                .orElseThrow(() -> new RuntimeException("고객을 찾을 수 없습니다: " + reservation.getCustomerId()));

        customer.refundReservedPoints(reservation.getPoints());
        customerRepository.save(customer);

        reservation.cancel();
        pointReservationRepository.save(reservation);

        log.info("포인트 예약 취소 완료: orderId={}, reservationId={}, refundedPoints={}",
                orderId, reservation.getReservationId(), reservation.getPoints());

        try {
            String url = PAYMENT_SERVICE_URL + "/refund/" + orderId;
            restTemplate.postForObject(url, null, String.class);
            log.info("PaymentService에 결제 상태 REFUNDED 업데이트 요청 완료: orderId={}", orderId);
        } catch (Exception e) {
            log.error("PaymentService에 결제 상태 REFUNDED 업데이트 요청 실패: orderId={}", orderId, e);
        }
    }

    public List<PointReservation> getActiveReservations(UUID customerId) {
        return pointReservationRepository.findActiveReservationsByCustomerId(customerId);
    }

    public List<PointReservation> getReservationsByStatus(UUID customerId, ReservationStatus status) {
        return pointReservationRepository.findByCustomerIdAndStatus(customerId, status);
    }

    public Optional<PointReservation> getReservation(UUID reservationId) {
        return pointReservationRepository.findById(reservationId);
    }

    public Optional<PointReservation> getReservationByOrderId(UUID orderId) {
        return pointReservationRepository.findByOrderId(orderId);
    }
}
