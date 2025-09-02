package com.eatcloud.adminservice.domain.category.repository;

import com.eatcloud.adminservice.domain.category.entity.StoreCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreCategoryRepository extends BaseCategoryRepository<StoreCategory> {
    boolean existsByCode(String code);
    boolean existsByName(String name);
}
