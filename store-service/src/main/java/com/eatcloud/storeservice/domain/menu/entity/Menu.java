package com.eatcloud.storeservice.domain.menu.entity;

import com.eatcloud.autotime.BaseTimeEntity;
import com.eatcloud.storeservice.domain.menu.dto.MenuUpdateRequestDto;
import com.eatcloud.storeservice.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "p_menus")
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Menu extends BaseTimeEntity {

    @Id
    @GeneratedValue
    @Column(name = "menu_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "menu_num", nullable = false)
    private int menuNum;

    @Column(name = "menu_name", nullable = false, length = 200)
    private String menuName;

    @Column(name = "menu_category_code", nullable = false, length = 100)
    private String menuCategoryCode;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_unlimited", nullable = false)
    @Builder.Default
    private Boolean isUnlimited = false;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private Integer stockQuantity = 0;

    public void updateMenu(MenuUpdateRequestDto dto) {
        if (dto.getMenuNum() != null) {
            this.menuNum = dto.getMenuNum();
        }
        this.menuName = dto.getMenuName();
        this.menuCategoryCode = dto.getMenuCategoryCode();
        this.price = dto.getPrice();
        this.description = dto.getDescription();
        this.isAvailable = (dto.getIsAvailable() != null) ? dto.getIsAvailable() : Boolean.TRUE;
        this.imageUrl = dto.getImageUrl();
        this.isUnlimited = dto.getIsUnlimited();
        this.stockQuantity = dto.getStockQuantity();
    }

}
