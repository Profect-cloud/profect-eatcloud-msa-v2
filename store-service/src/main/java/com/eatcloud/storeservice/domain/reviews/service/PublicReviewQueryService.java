package com.eatcloud.storeservice.domain.reviews.service;

import com.eatcloud.logging.annotation.Loggable;
import com.eatcloud.storeservice.domain.reviews.client.OrdersReviewClient;
import com.eatcloud.storeservice.domain.reviews.dto.*;
import com.eatcloud.storeservice.domain.reviews.util.SortWhitelist;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public class PublicReviewQueryService {

    private final OrdersReviewClient orders;

    @Transactional(readOnly = true)
    public PublicReviewListResponse list(UUID storeId, PublicReviewFilter filter, Pageable pageable) {
        pageable = SortWhitelist.enforce(pageable, Set.of("createdAt", "rating"));

        OrdersReviewPageResponse page = orders.fetchReviews(
                storeId,
                filter.getMinRating(),
                filter.getHasImage(),
                filter.getFrom(),
                filter.getTo(),
                pageable
        );

        RatingSummaryResponse summary = orders.fetchSummary(storeId);

        var items = page.getContent().stream().map(r ->
                PublicReviewItem.builder()
                        .reviewId(r.getReviewId())
                        .orderId(r.getOrderId())
                        .rating(r.getRating())
                        .content(r.getContent())
                        .createdAt(r.getCreatedAt())
                        .createdBy(r.getCreatedBy())
                        .updatedAt(r.getUpdatedAt())
                        .updatedBy(r.getUpdatedBy())
                        .build()
        ).collect(Collectors.toList());

        PublicReviewListResponse.PagePayload<PublicReviewItem> payload =
                PublicReviewListResponse.PagePayload.<PublicReviewItem>builder()
                        .content(items)
                        .page(page.getPage())
                        .size(page.getSize())
                        .totalElements(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .build();

        return PublicReviewListResponse.builder()
                .storeId(storeId)
                .ratingSummary(summary)
                .reviews(payload)
                .build();
    }

    @Transactional(readOnly = true)
    public RatingSummaryResponse summary(UUID storeId) {
        return orders.fetchSummary(storeId);
    }
}
