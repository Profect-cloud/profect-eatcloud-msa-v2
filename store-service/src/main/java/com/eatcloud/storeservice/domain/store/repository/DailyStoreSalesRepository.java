package com.eatcloud.storeservice.domain.store.repository;


import com.eatcloud.autotime.repository.SoftDeleteRepository;
import com.eatcloud.storeservice.domain.store.entity.DailyStoreSales;
import com.eatcloud.storeservice.domain.store.entity.DailyStoreSalesId;
import com.eatcloud.storeservice.domain.store.entity.QDailyStoreSales;
import com.eatcloud.storeservice.domain.store.entity.QStore;
import com.eatcloud.storeservice.global.queryDSL.SoftDeletePredicates;
import com.eatcloud.storeservice.global.queryDSL.SpringContext;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;


import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DailyStoreSalesRepository extends SoftDeleteRepository<DailyStoreSales, DailyStoreSalesId> {

    default List<DailyStoreSales> findByStoreIdAndDateRangeActive(UUID storeId, LocalDate startDate, LocalDate endDate) {
        JPAQueryFactory queryFactory = getQueryFactory();
        QDailyStoreSales sales = QDailyStoreSales.dailyStoreSales;
        QStore store = QStore.store;

        BooleanBuilder condition = new BooleanBuilder();
        condition.and(SoftDeletePredicates.salesWithStoreActive());
        condition.and(store.storeId.eq(storeId));

        if (startDate != null) {
            condition.and(sales.saleDate.goe(startDate));
        }
        if (endDate != null) {
            condition.and(sales.saleDate.loe(endDate));
        }

        return queryFactory
                .selectFrom(sales)
                .join(sales.store, store)
                .where(condition)
                .orderBy(sales.saleDate.asc())
                .fetch();
    }
    default JPAQueryFactory getQueryFactory() {
        return SpringContext.getBean(JPAQueryFactory.class);
    }
}