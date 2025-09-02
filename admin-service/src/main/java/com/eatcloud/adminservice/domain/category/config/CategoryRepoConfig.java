package com.eatcloud.adminservice.domain.category.config;

import com.eatcloud.adminservice.domain.category.entity.BaseCategory;
import com.eatcloud.adminservice.domain.category.repository.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class CategoryRepoConfig {

    @Bean
    public Map<String, BaseCategoryRepository<? extends BaseCategory>> categoryRepoMap(
            StoreCategoryRepository storeRepo,
            MidCategoryRepository midRepo,
            MenuCategoryRepository menuRepo
    ) {
        return Map.of(
                "store-categories", storeRepo,
                "mid-categories",   midRepo,
                "menu-categories",  menuRepo
        );
    }
}

