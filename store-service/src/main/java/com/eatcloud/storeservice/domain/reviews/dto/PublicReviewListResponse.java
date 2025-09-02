package com.eatcloud.storeservice.domain.reviews.dto;

import com.eatcloud.storeservice.domain.reviews.dto.PublicReviewItem;
import com.eatcloud.storeservice.domain.reviews.dto.RatingSummaryResponse;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PublicReviewListResponse {
    private UUID storeId;
    private RatingSummaryResponse ratingSummary;
    private PagePayload<PublicReviewItem> reviews;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PagePayload<T> {
        private List<T> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }
}
