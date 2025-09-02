package com.eatcloud.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.eatcloud.orderservice.entity.Review;
import com.eatcloud.autotime.repository.SoftDeleteRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends SoftDeleteRepository<Review, UUID> {

	boolean existsByOrderOrderIdAndDeletedAtIsNull(UUID orderId);

	@Query("SELECT r FROM Review r JOIN r.order o WHERE o.customerId = :customerId AND r.deletedAt IS NULL ORDER BY r.createdAt DESC")
	List<Review> findByOrderCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(@Param("customerId") UUID customerId);

	@Query("SELECT r FROM Review r JOIN r.order o WHERE o.storeId = :storeId AND r.deletedAt IS NULL ORDER BY r.createdAt DESC")
	List<Review> findByOrderStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(@Param("storeId") UUID storeId);

	@Query("SELECT r FROM Review r JOIN r.order o WHERE r.reviewId = :reviewId AND o.customerId = :customerId AND r.deletedAt IS NULL")
	Optional<Review> findByReviewIdAndOrderCustomerIdAndDeletedAtIsNull(
		@Param("reviewId") UUID reviewId,
		@Param("customerId") UUID customerId
	);

	@Query("SELECT r FROM Review r JOIN r.order o WHERE o.storeId = :storeId AND r.rating = :rating AND r.deletedAt IS NULL ORDER BY r.createdAt DESC")
	List<Review> findByOrderStoreIdAndRatingAndDeletedAtIsNullOrderByCreatedAtDesc(
		@Param("storeId") UUID storeId, 
		@Param("rating") BigDecimal rating
	);
}
