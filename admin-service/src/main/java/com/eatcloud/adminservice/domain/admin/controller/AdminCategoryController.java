package com.eatcloud.adminservice.domain.admin.controller;


import com.eatcloud.adminservice.domain.admin.dto.CategoryDto;
import com.eatcloud.adminservice.domain.admin.message.ResponseMessage;
import com.eatcloud.adminservice.domain.admin.service.GenericCategoryService;
import com.eatcloud.autoresponse.core.ApiResponse;
import com.eatcloud.autoresponse.core.ApiResponseStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/{categoryType}")
@Tag(
	name = "2-2. Admin Category API",
	description = "관리자가 다양한 카테고리(store, menu 등) CRUD를 수행하는 API"
)
public class AdminCategoryController {

	private final GenericCategoryService genericCategoryService;

	@Operation(summary = "1. 카테고리 생성")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ResponseMessage> createCategory(
		@PathVariable String categoryType, @RequestBody CategoryDto dto) {
		genericCategoryService.create(categoryType, dto);
		return ApiResponse.of(ApiResponseStatus.CREATED, ResponseMessage.CATEGORY_CREATE_SUCCESS);
	}

	@Operation(summary = "2. 카테고리 수정")
	@PutMapping("/{id}")
	@ResponseStatus(HttpStatus.OK)
	public ApiResponse<ResponseMessage> updateCategory(
		@PathVariable String categoryType, @PathVariable Integer id, @RequestBody CategoryDto dto) {
		genericCategoryService.update(categoryType, id, dto);
		return ApiResponse.of(ApiResponseStatus.OK, ResponseMessage.CATEGORY_UPDATE_SUCCESS);
	}

	@Operation(summary = "3. 카테고리 삭제")
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.OK)
	public ApiResponse<ResponseMessage> deleteCategory(
		@PathVariable String categoryType, @PathVariable Integer id) {
		genericCategoryService.delete(categoryType, id);
		return ApiResponse.success(ResponseMessage.CATEGORY_DELETE_SUCCESS);
	}

	@Operation(summary = "4. 카테고리 목록 조회")
	@GetMapping("/list")
	@ResponseStatus(HttpStatus.OK)
	public ApiResponse<List<CategoryDto>> listCategories(
		@PathVariable String categoryType) {
		List<CategoryDto> list = genericCategoryService.list(categoryType);
		return ApiResponse.success(list);
	}
}
