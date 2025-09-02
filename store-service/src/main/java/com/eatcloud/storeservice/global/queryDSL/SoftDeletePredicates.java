package com.eatcloud.storeservice.global.queryDSL;


import com.eatcloud.storeservice.domain.menu.entity.QDailyMenuSales;
import com.eatcloud.storeservice.domain.menu.entity.QMenu;
import com.eatcloud.storeservice.domain.store.entity.QDailyStoreSales;
import com.eatcloud.storeservice.domain.store.entity.QStore;
import com.querydsl.core.types.dsl.BooleanExpression;

public class SoftDeletePredicates {

    public static BooleanExpression storeActive() {
        return QStore.store.deletedAt.isNull();
    }

    public static BooleanExpression menuActive() {
        return QMenu.menu.deletedAt.isNull();
    }

    public static BooleanExpression dailyStoreSalesActive() {
        return QDailyStoreSales.dailyStoreSales.deletedAt.isNull();
    }

    public static BooleanExpression dailyMenuSalesActive() {
        return QDailyMenuSales.dailyMenuSales.deletedAt.isNull();
    }

    public static BooleanExpression salesWithStoreActive() {
        return dailyStoreSalesActive().and(storeActive());
    }

    public static BooleanExpression menuSalesWithStoreAndMenuActive() {
        return dailyMenuSalesActive()
                .and(storeActive())
                .and(menuActive());
    }
}
