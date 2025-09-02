package com.eatcloud.storeservice.domain.reviews.controller;

import com.eatcloud.storeservice.domain.reviews.dto.PublicReviewFilter;
import com.eatcloud.storeservice.domain.reviews.dto.PublicReviewListResponse;
import com.eatcloud.storeservice.domain.reviews.dto.RatingSummaryResponse;
import com.eatcloud.storeservice.domain.reviews.service.PublicReviewQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class PublicReviewController {

    private final PublicReviewQueryService service;

    @GetMapping("/{storeId}/reviews")
    public PublicReviewListResponse list(
            @PathVariable UUID storeId,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(required = false) Boolean hasImage,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        String[] sp = sort.split(",", 2);
        String prop = sp[0];
        Sort.Direction dir = (sp.length > 1 && "asc".equalsIgnoreCase(sp[1]))
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(new Sort.Order(dir, prop)));

        PublicReviewFilter filter = PublicReviewFilter.builder()
                .minRating(minRating)
                .hasImage(hasImage)
                .from(from)
                .to(to)
                .build();

        return service.list(storeId, filter, pageable);
    }

    @GetMapping("/{storeId}/ratings/summary")
    public RatingSummaryResponse summary(@PathVariable UUID storeId) {
        return service.summary(storeId);
    }
}
