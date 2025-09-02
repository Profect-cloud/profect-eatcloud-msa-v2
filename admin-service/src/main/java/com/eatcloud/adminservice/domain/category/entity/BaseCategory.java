package com.eatcloud.adminservice.domain.category.entity;

import com.eatcloud.autotime.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@MappedSuperclass
@Getter @Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class BaseCategory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    protected Integer id;

    @Column(name = "code", nullable = false, unique = true, length = 100)
    protected String code;

    @Column(name = "name", nullable = false, length = 100)
    protected String name;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    protected Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    protected Boolean isActive = true;

    @Column(name = "total_store_amount", nullable = false)
    @Builder.Default
    protected Integer totalStoreAmount = 0;
}
