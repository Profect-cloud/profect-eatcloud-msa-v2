package com.eatcloud.adminservice.domain.category.repository;

import com.eatcloud.adminservice.domain.category.entity.BaseCategory;
import com.eatcloud.autotime.repository.SoftDeleteRepository;
import com.eatcloud.logging.annotation.Loggable;

import org.springframework.data.jpa.repository.JpaRepository;

@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public interface BaseCategoryRepository<T extends BaseCategory> extends SoftDeleteRepository<T, Integer> {
}
