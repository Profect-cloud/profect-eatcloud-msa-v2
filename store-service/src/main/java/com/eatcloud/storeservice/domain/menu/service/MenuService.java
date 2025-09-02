package com.eatcloud.storeservice.domain.menu.service;

import com.eatcloud.storeservice.domain.menu.entity.Menu;
import com.eatcloud.storeservice.domain.menu.exception.MenuErrorCode;
import com.eatcloud.storeservice.domain.menu.exception.MenuException;
import com.eatcloud.storeservice.domain.menu.repository.MenuRepository;
import com.eatcloud.storeservice.domain.store.entity.Store;
import com.eatcloud.storeservice.domain.store.exception.StoreErrorCode;
import com.eatcloud.storeservice.domain.store.exception.StoreException;
import com.eatcloud.storeservice.domain.store.repository.StoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;


@Service
public class MenuService {

    private final MenuRepository menuRepository;
    private final StoreRepository storeRepository;

    @Autowired
    public MenuService(MenuRepository menuRepository, StoreRepository storeRepository) {
        this.menuRepository = menuRepository;
        this.storeRepository = storeRepository;
    }

    public List<Menu> getMenusByStore(UUID storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreException(StoreErrorCode.STORE_NOT_FOUND));

        return menuRepository.findAllByStore(store);
    }

    public Menu getMenuById(UUID storeId, UUID menuId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreException(StoreErrorCode.STORE_NOT_FOUND));

        return menuRepository.findByIdAndStore(menuId, store)
                .orElseThrow(() -> new MenuException(MenuErrorCode.MENU_NOT_FOUND));
    }
}
