package com.eatcloud.adminservice.domain.category.repository;

import com.eatcloud.adminservice.domain.category.entity.StoreCategory;
import com.eatcloud.logging.annotation.Loggable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public interface StoreCategoryRepository extends BaseCategoryRepository<StoreCategory> {
    boolean existsByCode(String code);
    boolean existsByName(String name);
}
