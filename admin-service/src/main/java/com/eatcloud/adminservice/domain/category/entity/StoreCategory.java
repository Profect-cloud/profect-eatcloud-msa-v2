package com.eatcloud.adminservice.domain.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "p_store_categories")
@SQLRestriction("deleted_at is null")
@Getter @Setter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StoreCategory extends BaseCategory {


    // 상위 카테고리는 code 길이를 50으로 제한하고 싶다면 이렇게 재정의 가능
    @Override
    @Column(name = "code", nullable = false, unique = true, length = 50)
    public String getCode() { return super.code; }
    @Override
    public void setCode(String code) { super.code = code; }
}
