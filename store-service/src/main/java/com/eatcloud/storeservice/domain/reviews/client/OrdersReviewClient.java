package com.eatcloud.storeservice.domain.reviews.client;

import com.eatcloud.storeservice.domain.reviews.dto.OrdersReviewPageResponse;
import com.eatcloud.storeservice.domain.reviews.dto.RatingSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrdersReviewClient {

    private final RestClient ordersRestClient;

    public OrdersReviewPageResponse fetchReviews(
            UUID storeId, Integer minRating, Boolean hasImage,
            LocalDateTime from, LocalDateTime to, org.springframework.data.domain.Pageable pageable
    ) {
        String sortParam = pageable.getSort().stream().findFirst()
                .map(o -> o.getProperty() + "," + o.getDirection().name().toLowerCase())
                .orElse("createdAt,desc");

        String uri = UriComponentsBuilder.fromPath("/internal/v1/reviews")
                .queryParam("storeId", storeId)
                .queryParamIfPresent("minRating", Optional.ofNullable(minRating))
                .queryParamIfPresent("hasImage", Optional.ofNullable(hasImage))
                .queryParamIfPresent("from", Optional.ofNullable(from))
                .queryParamIfPresent("to", Optional.ofNullable(to))
                .queryParam("page", pageable.getPageNumber())
                .queryParam("size", pageable.getPageSize())
                .queryParam("sort", sortParam)
                .build(true).toUriString();

        return ordersRestClient.get()
                .uri(uri)
                .retrieve()
                .body(OrdersReviewPageResponse.class);
    }

    public RatingSummaryResponse fetchSummary(UUID storeId) {
        String uri = UriComponentsBuilder.fromPath("/internal/v1/reviews/summary")
                .queryParam("storeId", storeId)
                .build(true).toUriString();

        return ordersRestClient.get()
                .uri(uri)
                .retrieve()
                .body(RatingSummaryResponse.class);
    }
}
