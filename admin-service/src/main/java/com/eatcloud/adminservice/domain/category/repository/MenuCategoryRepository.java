package com.eatcloud.adminservice.domain.category.repository;


import com.eatcloud.adminservice.domain.category.entity.MenuCategory;
import com.eatcloud.logging.annotation.Loggable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public interface MenuCategoryRepository extends BaseCategoryRepository<MenuCategory> {
    boolean existsByMidCategoryId(Integer midCategoryId);
    boolean existsByMidCategoryIdAndName(Integer midCategoryId, String name);
    Optional<MenuCategory> findByCode(String code);
}
