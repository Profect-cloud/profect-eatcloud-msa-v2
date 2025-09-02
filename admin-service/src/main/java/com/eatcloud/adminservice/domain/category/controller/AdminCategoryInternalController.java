package com.eatcloud.adminservice.domain.category.controller;

import com.eatcloud.adminservice.domain.admin.dto.CategoryDto;
import com.eatcloud.adminservice.domain.category.entity.MenuCategory;
import com.eatcloud.adminservice.domain.category.entity.StoreCategory;
import com.eatcloud.adminservice.domain.category.repository.MenuCategoryRepository;
import com.eatcloud.adminservice.domain.category.repository.StoreCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/categories")
public class AdminCategoryInternalController {

    private final StoreCategoryRepository storeRepo;
    private final MenuCategoryRepository  menuRepo;

    @GetMapping("/store/{id}")
    public CategoryDto getStore(@PathVariable Integer id) {
        StoreCategory e = storeRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toDto(e);
    }

    @GetMapping("/menu/{id}")
    public CategoryDto getMenu(@PathVariable Integer id) {
        MenuCategory e = menuRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toDto(e);
    }

    @GetMapping("/menu/by-code/{code}")
    public CategoryDto getMenuByCode(@PathVariable String code) {
        MenuCategory e = menuRepo.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toDto(e);
    }

    @GetMapping("/store")
    public List<CategoryDto> listStore() {
        return storeRepo.findAll().stream().map(this::toDto).toList();
    }

    @GetMapping("/menu")
    public List<CategoryDto> listMenu() {
        return menuRepo.findAll().stream().map(this::toDto).toList();
    }

    private CategoryDto toDto(StoreCategory e) {
        return CategoryDto.builder()
                .id(e.getId()).code(e.getCode())
                .displayName(e.getName())
                .sortOrder(e.getSortOrder())
                .isActive(e.getIsActive())
                .build();
    }
    private CategoryDto toDto(MenuCategory e) {
        return CategoryDto.builder()
                .id(e.getId()).code(e.getCode())
                .displayName(e.getName())
                .sortOrder(e.getSortOrder())
                .isActive(e.getIsActive())
                .build();
    }
}
