package com.eatcloud.customerservice.repository;

import com.eatcloud.autotime.repository.SoftDeleteRepository;
import com.eatcloud.customerservice.entity.PointReservation;
import com.eatcloud.customerservice.entity.ReservationStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PointReservationRepository extends SoftDeleteRepository<PointReservation, UUID> {
    
    Optional<PointReservation> findByOrderId(UUID orderId);
    
    List<PointReservation> findByCustomerId(UUID customerId);
    
    List<PointReservation> findByCustomerIdAndStatus(UUID customerId, ReservationStatus status);
    
    @Query("SELECT pr FROM PointReservation pr WHERE pr.customerId = :customerId AND pr.status = 'RESERVED'")
    List<PointReservation> findActiveReservationsByCustomerId(@Param("customerId") UUID customerId);
    
    @Query("SELECT COUNT(pr) FROM PointReservation pr WHERE pr.customerId = :customerId AND pr.status = 'RESERVED'")
    long countActiveReservationsByCustomerId(@Param("customerId") UUID customerId);
    
    boolean existsByOrderId(UUID orderId);
    
    boolean existsByCustomerIdAndOrderId(UUID customerId, UUID orderId);
} 