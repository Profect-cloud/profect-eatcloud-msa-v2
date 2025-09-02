package com.eatcloud.storeservice.domain.store.repository;


import com.eatcloud.autotime.repository.SoftDeleteRepository;
import com.eatcloud.storeservice.domain.store.entity.Store;

import java.util.Optional;
import java.util.UUID;

public interface StoreRepository extends SoftDeleteRepository<Store, UUID>, StoreCustomRepository{

    // 메뉴 등록 시 storeId로 가게 정보 조회
    Optional<Store> findById(UUID storeId);

    boolean existsByStoreIdAndManagerId(UUID storeId, UUID managerId);

    // (선택) 단순히 존재만 확인할 때
    boolean existsById(UUID storeId);

    boolean existsByManagerIdAndStoreName(UUID managerId, String storeName);
}