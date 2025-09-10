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
    private final CustomerService customerService;

    private static final String PAYMENT_SERVICE_URL = "http://payment-service/api/v1/payments";

    @Transactional
    public PointReservation createReservation(UUID customerId, UUID orderId, Integer points) {
        log.info("포인트 예약 생성 시작: customerId={}, orderId={}, points={}", customerId, orderId, points);

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("고객을 찾을 수 없습니다: " + customerId));

        if (pointReservationRepository.existsByOrderId(orderId)) {
            throw new IllegalStateException("이미 포인트 예약이 존재합니다: orderId=" + orderId);
        }

        customerService.reservePoints(customerId, points);

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
        log.info("포인트 예약 처리 시작 (실제 차감): orderId={}", orderId);

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

        // 예약된 포인트를 실제로 차감
        customerService.processReservedPoints(reservation.getCustomerId(), reservation.getPoints());

        // 예약 상태를 PROCESSED로 변경
        reservation.process();
        pointReservationRepository.save(reservation);

        log.info("포인트 예약 처리 완료 (실제 차감됨): orderId={}, customerId={}, points={}, reservationId={}", 
                orderId, reservation.getCustomerId(), reservation.getPoints(), reservation.getReservationId());
    }

    @Transactional
    public void cancelReservation(UUID orderId) {
        log.info("포인트 예약 취소 시작 (예약 해제): orderId={}", orderId);

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

        customerService.cancelReservedPoints(reservation.getCustomerId(), reservation.getPoints());

        reservation.cancel();
        pointReservationRepository.save(reservation);

        log.info("포인트 예약 취소 완료 (예약 해제됨): orderId={}, customerId={}, points={}, reservationId={}",
                orderId, reservation.getCustomerId(), reservation.getPoints(), reservation.getReservationId());

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
