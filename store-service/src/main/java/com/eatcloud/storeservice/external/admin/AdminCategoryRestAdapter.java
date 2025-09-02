package com.eatcloud.storeservice.external.admin;

import com.eatcloud.storeservice.external.admin.dto.CategoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

@Component
public class AdminCategoryRestAdapter implements AdminCategoryPort {

    private final RestClient adminClient;

    public AdminCategoryRestAdapter(@Qualifier("adminRestClient") RestClient adminClient) {
        this.adminClient = adminClient;
    }

    @Override
    public CategoryDto getStoreCategoryById(Integer id) {
        CategoryDto dto = adminClient.get()
                .uri("/internal/admin/categories/store/{id}", id)
                .retrieve()
                .body(CategoryDto.class);
        if (dto == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "store-category not found");
        return dto;
    }

    @Override
    public CategoryDto getMenuCategoryById(Integer id) {
        CategoryDto dto = adminClient.get()
                .uri("/internal/admin/categories/menu/{id}", id)
                .retrieve()
                .body(CategoryDto.class);
        if (dto == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "menu-category not found");
        return dto;
    }

    @Override
    public CategoryDto getMenuCategoryByCode(String code) {
        CategoryDto dto = adminClient.get()
                .uri("/internal/admin/categories/menu/by-code/{code}", code)
                .retrieve()
                .body(CategoryDto.class);
        if (dto == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "menu-category not found");
        return dto;
    }

    @Override
    public List<CategoryDto> listStoreCategories() {
        CategoryDto[] arr = adminClient.get()
                .uri("/internal/categories/store")
                .retrieve()
                .body(CategoryDto[].class);
        return arr == null ? List.of() : Arrays.asList(arr);
    }

    @Override
    public List<CategoryDto> listMenuCategories() {
        CategoryDto[] arr = adminClient.get()
                .uri("/internal/categories/menu")
                .retrieve()
                .body(CategoryDto[].class);
        return arr == null ? List.of() : Arrays.asList(arr);
    }
}
