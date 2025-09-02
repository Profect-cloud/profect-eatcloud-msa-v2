// MenuCategory.java
package com.eatcloud.adminservice.domain.category.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "p_menu_categories")
@SQLRestriction("deleted_at is null")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MenuCategory extends BaseCategory {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_category_id", nullable = false)
    private StoreCategory storeCategory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mid_category_id", nullable = false)
    private MidCategory midCategory;

    @PrePersist @PreUpdate
    private void alignStoreCategory() {
        if (midCategory != null && midCategory.getStoreCategory() != null) {
            this.storeCategory = midCategory.getStoreCategory();
        }
    }
}
