
package com.eatcloud.storeservice.domain.menu.repository;

import com.eatcloud.autotime.repository.SoftDeleteRepository;
import com.eatcloud.storeservice.domain.menu.entity.DailyMenuSales;
import com.eatcloud.storeservice.domain.menu.entity.DailyMenuSalesId;
import com.eatcloud.storeservice.domain.menu.entity.QDailyMenuSales;
import com.eatcloud.storeservice.domain.menu.entity.QMenu;
import com.eatcloud.storeservice.domain.store.dto.MenuSalesAggregationDto;
import com.eatcloud.storeservice.domain.store.entity.QStore;
import com.eatcloud.storeservice.global.queryDSL.SoftDeletePredicates;
import com.eatcloud.storeservice.global.queryDSL.SpringContext;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DailyMenuSalesRepository extends SoftDeleteRepository<DailyMenuSales, DailyMenuSalesId> {

    default List<MenuSalesAggregationDto> getMenuSalesRanking(
            UUID storeId, LocalDate startDate, LocalDate endDate, int limit) {

        JPAQueryFactory queryFactory = getQueryFactory();
        QDailyMenuSales menuSales = QDailyMenuSales.dailyMenuSales;
        QStore store = QStore.store;
        QMenu menu = QMenu.menu;

        BooleanBuilder condition = new BooleanBuilder();
        condition.and(SoftDeletePredicates.menuSalesWithStoreAndMenuActive());
        condition.and(store.storeId.eq(storeId));

        if (startDate != null) {
            condition.and(menuSales.saleDate.goe(startDate));
        }
        if (endDate != null) {
            condition.and(menuSales.saleDate.loe(endDate));
        }

        return queryFactory
                .select(Projections.constructor(MenuSalesAggregationDto.class,
                        menu.id,
                        menu.menuName,
                        menuSales.quantitySold.sum(),
                        menuSales.totalAmount.sum()
                ))
                .from(menuSales)
                .join(menuSales.store, store)
                .join(menuSales.menu, menu)
                .where(condition)
                .groupBy(menu.id, menu.menuName)
                .orderBy(menuSales.totalAmount.sum().desc())
                .limit(limit)
                .fetch();
    }

    default JPAQueryFactory getQueryFactory() {
        return SpringContext.getBean(JPAQueryFactory.class);
    }
}