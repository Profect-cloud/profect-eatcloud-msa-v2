package com.eatcloud.storeservice.domain.manager.service;

import com.eatcloud.storeservice.domain.menu.dto.MenuRequestDto;
import com.eatcloud.storeservice.domain.menu.dto.MenuUpdateRequestDto;
import com.eatcloud.storeservice.domain.menu.entity.Menu;
import com.eatcloud.storeservice.domain.menu.exception.MenuErrorCode;
import com.eatcloud.storeservice.domain.menu.exception.MenuException;
import com.eatcloud.storeservice.domain.menu.repository.MenuRepository;
import com.eatcloud.storeservice.domain.menuai.service.TFIDFService;
import com.eatcloud.storeservice.domain.store.dto.StoreCreateRequestDto;
import com.eatcloud.storeservice.domain.store.dto.StoreUpdateRequestDto;
import com.eatcloud.storeservice.domain.store.entity.Store;
import com.eatcloud.storeservice.domain.store.exception.StoreErrorCode;
import com.eatcloud.storeservice.domain.store.exception.StoreException;
import com.eatcloud.storeservice.domain.store.repository.StoreRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ManagerService {

    private final MenuRepository menuRepository;
    private final StoreRepository storeRepository;
    private final TFIDFService tfidfService;

    @Autowired
    public ManagerService(MenuRepository menuRepository, StoreRepository storeRepository, TFIDFService tfidfService) {
        this.menuRepository = menuRepository;
        this.storeRepository = storeRepository;
        this.tfidfService = tfidfService;
    }

    // 메뉴 생성
    @Transactional
    public Menu createMenu(UUID storeId, MenuRequestDto dto) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreException(StoreErrorCode.STORE_NOT_FOUND));

        if (dto.getPrice() == null || dto.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new MenuException(MenuErrorCode.INVALID_MENU_PRICE);
        }

        if (dto.getMenuName() == null || dto.getMenuName().trim().isEmpty()) {
            throw new MenuException(MenuErrorCode.MENU_NAME_REQUIRED);
        }

        Boolean isAvailable = dto.getIsAvailable();
        if (isAvailable == null) {
            isAvailable = true;
        }

        if (menuRepository.existsByStoreAndMenuNum(store, dto.getMenuNum())) {
            throw new MenuException(MenuErrorCode.DUPLICATE_MENU_NUM);
        }

        Menu menu = Menu.builder()
                .store(store)
                .menuNum(dto.getMenuNum())
                .menuName(dto.getMenuName())
                .menuCategoryCode(dto.getMenuCategoryCode())
                .price(dto.getPrice())
                .description(dto.getDescription())
                .isAvailable(isAvailable)
                .imageUrl(dto.getImageUrl())
                .build();

        Menu savedMenu = menuRepository.save(menu);
        log.info("메뉴 생성 완료: {}", savedMenu.getMenuName());

        // TF-IDF 벡터 생성 및 저장 (동기 처리로 변경)
        try {
            tfidfService.generateAndSaveMenuVector(savedMenu);
            log.info("메뉴 벡터 생성 완료: {}", savedMenu.getMenuName());
        } catch (Exception e) {
            log.error("메뉴 벡터 생성 실패: {}", savedMenu.getMenuName(), e);
        }

        return savedMenu;
    }

    @Transactional
    public Menu updateMenu(UUID storeId, UUID menuId, MenuUpdateRequestDto dto) {
        // 1) 메뉴 존재 확인
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new MenuException(MenuErrorCode.MENU_NOT_FOUND));

        // 2) 이 메뉴가 해당 store 소속인지 확인 (권한/무결성)
        if (!menu.getStore().getStoreId().equals(storeId)) {
            throw new StoreException(StoreErrorCode.STORE_NOT_FOUND); // 또는 FORBIDDEN 성격의 에러
        }

        // 3) 필수 검증
        if (dto.getMenuName() == null || dto.getMenuName().trim().isEmpty()) {
            throw new MenuException(MenuErrorCode.MENU_NAME_REQUIRED);
        }
        if (dto.getPrice() == null || dto.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new MenuException(MenuErrorCode.INVALID_MENU_PRICE);
        }

        // 4) menuNum을 업데이트 허용한다면 중복 체크 (허용 안 하면 이 블록 삭제)
        if (dto.getMenuNum() != null && !dto.getMenuNum().equals(menu.getMenuNum())) {
            boolean exists = menuRepository.existsByStoreAndMenuNum(menu.getStore(), dto.getMenuNum());
            if (exists) {
                throw new MenuException(MenuErrorCode.DUPLICATE_MENU_NUM);
            }
        }

        // 5) 엔티티 도메인 메서드로 변경
        menu.updateMenu(dto);

        // 6) 더티체킹으로 자동 flush → save 굳이 필요 없음 (있어도 무방)
        Menu updatedMenu = menuRepository.save(menu);

        // 7) 커밋 이후 벡터 재생성 (간단하게 비동기 유지)
        CompletableFuture.runAsync(() -> {
            try {
                tfidfService.generateAndSaveMenuVector(menu);
            } catch (Exception e) {
                log.error("메뉴 벡터 재생성 실패: {}", menu.getMenuName(), e);
            }
        });

        return menu;
    }

    @Transactional
    public void deleteMenu(UUID menuId) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new MenuException(MenuErrorCode.MENU_NOT_FOUND));

        String menuName = menu.getMenuName();
        menuRepository.softDeleteById(menuId,"매니저");
        log.info("메뉴 삭제 완료: {}", menuName);

        // 벡터도 함께 삭제 (비동기 처리)
        CompletableFuture.runAsync(() -> {
            try {
                tfidfService.deleteMenuVector(menuName);
            } catch (Exception e) {
                log.error("메뉴 벡터 삭제 실패: {}", menuName, e);
            }
        });
    }

    public void updateStore(UUID storeId, StoreUpdateRequestDto dto) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreException(StoreErrorCode.STORE_NOT_FOUND));

    }

    // 가게 생성

    @Transactional
    public Store createStore(UUID managerId, StoreCreateRequestDto dto) {
        // 가게명 중복 체크
        if (storeRepository.existsByManagerIdAndStoreName((managerId), dto.getStoreName())) {
            throw new StoreException(StoreErrorCode.STORE_ALREADY_REGISTERED);
        }

        // 빌더로 Store 생성
        Store store = Store.builder()
                .managerId(managerId)
                .storeName(dto.getStoreName())
                .storeAddress(dto.getStoreAddress())
                .phoneNumber(dto.getPhoneNumber())
                .storeCategoryId(dto.getStoreCategoryId())
                .minCost(dto.getMinCost())
                .description(dto.getDescription())
                .openStatus(false)         // 신규 등록은 기본 false
                .openTime(null)            // 필요시 null 허용
                .closeTime(null)
                .storeLat(dto.getStoreLat())
                .storeLon(dto.getStoreLon())
                .build();

        return storeRepository.save(store);
    }

    @Transactional
    public void deleteStore(UUID storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreException(StoreErrorCode.STORE_ALREADY_CLOSED));

        storeRepository.softDeleteById(store.getStoreId(), "MANAGER");
    }

}
