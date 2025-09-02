package com.eatcloud.storeservice.external.admin;

import com.eatcloud.storeservice.external.admin.dto.CategoryDto;

import java.util.List;

public interface AdminCategoryPort {
    CategoryDto getStoreCategoryById(Integer id);
    CategoryDto getMenuCategoryById(Integer id);
    CategoryDto getMenuCategoryByCode(String code);

    List<CategoryDto> listStoreCategories();
    List<CategoryDto> listMenuCategories();
}
