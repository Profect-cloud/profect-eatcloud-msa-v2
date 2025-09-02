package com.eatcloud.storeservice.domain.reviews.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PublicReviewFilter {
    private Integer minRating;
    private Boolean hasImage;
    private LocalDateTime from;
    private LocalDateTime to;
}
