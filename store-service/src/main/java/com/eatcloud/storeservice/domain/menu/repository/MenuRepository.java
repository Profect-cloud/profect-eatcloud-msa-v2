package com.eatcloud.storeservice.domain.menu.repository;


import com.eatcloud.autotime.repository.SoftDeleteRepository;
import com.eatcloud.storeservice.domain.menu.entity.Menu;
import com.eatcloud.storeservice.domain.store.entity.Store;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuRepository extends SoftDeleteRepository<Menu, UUID> {
    List<Menu> findByStoreStoreId(UUID storeId);

    boolean existsByStoreAndMenuNum(Store store, int menuNum);

    List<Menu> findAllByStore(Store store);
    Optional<Menu> findByIdAndStore(UUID id, Store store);

    @Query("SELECT DISTINCT m.store FROM Menu m WHERE m.menuCategoryCode = :code AND m.isAvailable = true")
    List<Store> findDistinctStoresByMenuCategoryCode(@Param("code") String code);
}
