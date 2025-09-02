package com.eatcloud.storeservice.domain.reviews.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RatingSummaryResponse {
    private BigDecimal avgRating;
    private int ratingCount;
    private BigDecimal rating30dAvg;
    private Map<Integer, Integer> histogram;
}
