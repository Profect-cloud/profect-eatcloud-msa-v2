package com.eatcloud.storeservice.domain.reviews.dto;
import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrdersReviewPageResponse {
    private List<OrdersReviewItem> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
