package com.eatcloud.adminservice.domain.category.repository;


import com.eatcloud.adminservice.domain.category.entity.MidCategory;
import com.eatcloud.logging.annotation.Loggable;

import org.springframework.data.jpa.repository.JpaRepository;

@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public interface MidCategoryRepository extends BaseCategoryRepository<MidCategory> {
    boolean existsByStoreCategoryId(Integer storeCategoryId);
    boolean existsByStoreCategoryIdAndName(Integer storeCategoryId, String name);
}
