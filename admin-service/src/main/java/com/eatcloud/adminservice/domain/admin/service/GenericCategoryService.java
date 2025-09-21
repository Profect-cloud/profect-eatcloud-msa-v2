package com.eatcloud.adminservice.domain.admin.service;

import com.eatcloud.adminservice.domain.admin.dto.CategoryDto;
import com.eatcloud.adminservice.domain.admin.exception.AdminErrorCode;
import com.eatcloud.adminservice.domain.admin.exception.AdminException;
import com.eatcloud.adminservice.domain.category.entity.*;
import com.eatcloud.adminservice.domain.category.repository.*;
import com.eatcloud.logging.annotation.Loggable;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true)
public class GenericCategoryService {

	private final Map<String, BaseCategoryRepository<? extends BaseCategory>> repoMap;

	private final StoreCategoryRepository storeRepo;
	private final MidCategoryRepository midRepo;
	private final MenuCategoryRepository menuRepo;

	public GenericCategoryService(
			Map<String, BaseCategoryRepository<? extends BaseCategory>> repoMap,
			StoreCategoryRepository storeRepo,
			MidCategoryRepository midRepo,
			MenuCategoryRepository menuRepo
	) {
		this.repoMap = repoMap;
		this.storeRepo = storeRepo;
		this.midRepo = midRepo;
		this.menuRepo = menuRepo;
	}

	private BaseCategoryRepository<BaseCategory> repo(String type) {
		@SuppressWarnings("unchecked")
		var r = (BaseCategoryRepository<BaseCategory>) repoMap.get(type);
		if (r == null) throw new AdminException(AdminErrorCode.INVALID_INPUT);
		return r;
	}

	@Transactional
	public CategoryDto create(String type, CategoryDto dto) {
		BaseCategory entity = switch (type) {
			case "store-categories" -> buildStore(dto);
			case "mid-categories"   -> buildMid(dto);
			case "menu-categories"  -> buildMenu(dto);
			default -> throw new AdminException(AdminErrorCode.INVALID_INPUT);
		};
		BaseCategory saved = repo(type).save(entity);
		return toDto(saved);
	}

	private StoreCategory buildStore(CategoryDto d) {
		var e = new StoreCategory();
		fillBase(e, d);
		return e;
	}

	private MidCategory buildMid(CategoryDto d) {
		if (d.getStoreCategoryId() == null) throw new AdminException(AdminErrorCode.INVALID_INPUT);
		StoreCategory parent = storeRepo.findById(d.getStoreCategoryId())
				.orElseThrow(() -> new AdminException(AdminErrorCode.CATEGORY_NOT_FOUND));
		var e = new MidCategory();
		fillBase(e, d);
		e.setStoreCategory(parent);
		return e;
	}

	private MenuCategory buildMenu(CategoryDto d) {
		if (d.getMidCategoryId() == null) throw new AdminException(AdminErrorCode.INVALID_INPUT);
		MidCategory mid = midRepo.findById(d.getMidCategoryId())
				.orElseThrow(() -> new AdminException(AdminErrorCode.CATEGORY_NOT_FOUND));
		StoreCategory storeFromMid = mid.getStoreCategory();

		StoreCategory storeToUse = storeFromMid;
		if (d.getStoreCategoryId() != null) {
			if (!d.getStoreCategoryId().equals(storeFromMid.getId())) {
				throw new AdminException(AdminErrorCode.INVALID_INPUT);
			}
		}
		var e = new MenuCategory();
		fillBase(e, d);
		e.setMidCategory(mid);
		e.setStoreCategory(storeToUse);
		return e;
	}

	@Transactional
	public CategoryDto update(String type, Integer id, CategoryDto dto) {
		BaseCategoryRepository<BaseCategory> repository = repo(type);
		BaseCategory entity = repository.findById(id)
				.orElseThrow(() -> new AdminException(AdminErrorCode.CATEGORY_NOT_FOUND));

		fillBase(entity, dto);

		switch (type) {
			case "mid-categories" -> {
				MidCategory mid = (MidCategory) entity;
				if (dto.getStoreCategoryId() != null &&
						(mid.getStoreCategory() == null ||
								!dto.getStoreCategoryId().equals(mid.getStoreCategory().getId()))) {
					StoreCategory parent = storeRepo.findById(dto.getStoreCategoryId())
							.orElseThrow(() -> new AdminException(AdminErrorCode.CATEGORY_NOT_FOUND));
					mid.setStoreCategory(parent);
				}
			}
			case "menu-categories" -> {
				MenuCategory menu = (MenuCategory) entity;
				if (dto.getMidCategoryId() != null &&
						(menu.getMidCategory() == null ||
								!dto.getMidCategoryId().equals(menu.getMidCategory().getId()))) {
					MidCategory newMid = midRepo.findById(dto.getMidCategoryId())
							.orElseThrow(() -> new AdminException(AdminErrorCode.CATEGORY_NOT_FOUND));
					menu.setMidCategory(newMid);
					menu.setStoreCategory(newMid.getStoreCategory()); // 정합성 유지
				}
				if (dto.getStoreCategoryId() != null &&
						!dto.getStoreCategoryId().equals(menu.getStoreCategory().getId())) {
					// menu의 storeCategory는 mid의 상위와 일치해야 함
					throw new AdminException(AdminErrorCode.INVALID_INPUT);
				}
			}
		}

		BaseCategory updated = repository.save(entity);
		return toDto(updated);
	}

	@Transactional
	public void delete(String type, Integer id) {
		BaseCategoryRepository<BaseCategory> repository = repo(type);
		BaseCategory entity = repository.findById(id)
				.orElseThrow(() -> new AdminException(AdminErrorCode.CATEGORY_NOT_FOUND));

		switch (type) {
			case "store-categories" -> {
				if (midRepo.existsByStoreCategoryId(id)) {
					throw new AdminException(AdminErrorCode.INVALID_INPUT); // "하위(Mid) 존재"
				}
			}
			case "mid-categories" -> {
				if (menuRepo.existsByMidCategoryId(id)) {
					throw new AdminException(AdminErrorCode.INVALID_INPUT); // "하위(Menu) 존재"
				}
			}
		}

		repository.softDelete(entity,"admin");
	}

	public List<CategoryDto> list(String type) {
		return repo(type)
				.findAll(Sort.by("sortOrder").ascending().and(Sort.by("id").ascending()))
				.stream()
				.map(this::toDto)
				.toList();
	}

	private void fillBase(BaseCategory e, CategoryDto d) {
		e.setCode(d.getCode());
		e.setName(d.getDisplayName()); // DTO의 displayName -> 엔티티 name
		if (d.getSortOrder() != null) e.setSortOrder(d.getSortOrder());
		if (d.getIsActive() != null)  e.setIsActive(d.getIsActive());
		if (d.getTotalStoreAmount() != null) e.setTotalStoreAmount(d.getTotalStoreAmount());
	}

	private CategoryDto toDto(BaseCategory e) {
		CategoryDto.CategoryDtoBuilder b = CategoryDto.builder()
				.id(e.getId())
				.code(e.getCode())
				.displayName(e.getName())
				.sortOrder(e.getSortOrder())
				.isActive(e.getIsActive())
				.totalStoreAmount(e.getTotalStoreAmount());

		if (e instanceof MidCategory mid) {
			if (mid.getStoreCategory() != null) {
				b.storeCategoryId(mid.getStoreCategory().getId());
			}
		} else if (e instanceof MenuCategory menu) {
			if (menu.getStoreCategory() != null) {
				b.storeCategoryId(menu.getStoreCategory().getId());
			}
			if (menu.getMidCategory() != null) {
				b.midCategoryId(menu.getMidCategory().getId());
			}
		}
		return b.build();
	}
}
