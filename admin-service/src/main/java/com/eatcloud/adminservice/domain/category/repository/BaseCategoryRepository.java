package com.eatcloud.adminservice.domain.category.repository;

import com.eatcloud.adminservice.domain.category.entity.BaseCategory;
import com.eatcloud.autotime.repository.SoftDeleteRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaseCategoryRepository<T extends BaseCategory> extends SoftDeleteRepository<T, Integer> {
}
