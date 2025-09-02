package com.eatcloud.storeservice.domain.store.entity;



import com.eatcloud.autotime.BaseTimeEntity;
import com.eatcloud.storeservice.domain.store.dto.StoreCreateRequestDto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;


@Entity
@SQLRestriction("deleted_at is null")
@Table(name = "p_stores")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Store extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "store_id")
    private UUID storeId;

    // ✅ 사장 연관 제거 → 식별자만 보관
    @Column(name = "manager_id", nullable = true, columnDefinition = "uuid")
    private UUID managerId;

    @Column(name = "store_name", nullable = false, length = 200)
    private String storeName;

    @Column(name = "store_address", length = 300)
    private String storeAddress;

    @Column(name = "phone_number", length = 18)
    private String phoneNumber;

    @Column(name = "min_cost", nullable = false)
    @Builder.Default
    private Integer minCost = 0;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "store_lat")
    private Double storeLat;

    @Column(name = "store_lon")
    private Double storeLon;

    @Column(columnDefinition = "GEOGRAPHY(Point, 4326)")
    private Point location;

    @Column(name = "open_status")
    private Boolean openStatus;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Column(name = "store_category_id", nullable = false)
    private Integer storeCategoryId;

    // ⭐ 평점 집계 (스키마: rating_sum NUMERIC(10,2), rating_count INT, avg_rating NUMERIC(3,2))
    @Column(name = "rating_sum", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal ratingSum = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    @Builder.Default
    private Integer ratingCount = 0;

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal avgRating = BigDecimal.ZERO;


}