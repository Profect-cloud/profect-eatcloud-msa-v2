package com.eatcloud.adminservice.domain.category.repository;


import com.eatcloud.adminservice.domain.category.entity.MidCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MidCategoryRepository extends BaseCategoryRepository<MidCategory> {
    boolean existsByStoreCategoryId(Integer storeCategoryId);
    boolean existsByStoreCategoryIdAndName(Integer storeCategoryId, String name);
}
