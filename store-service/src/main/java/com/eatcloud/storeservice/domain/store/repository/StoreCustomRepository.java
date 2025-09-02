package com.eatcloud.storeservice.domain.store.repository;


import com.eatcloud.storeservice.domain.store.dto.StoreKeywordSearchRequestDto;
import com.eatcloud.storeservice.domain.store.dto.StoreSearchResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface StoreCustomRepository {
    List<StoreSearchResponseDto> findStoresByCategoryWithinDistance(
            UUID categoryId, double userLat, double userLon, double distanceKm
    );

    List<StoreSearchResponseDto> findStoresByMenuCategoryWithinDistance(
            String menuCategoryCode, double userLat, double userLon, double distanceKm);

    Page<StoreSearchResponseDto> searchByKeywordAndCategory(StoreKeywordSearchRequestDto req, Pageable pageable);
}


