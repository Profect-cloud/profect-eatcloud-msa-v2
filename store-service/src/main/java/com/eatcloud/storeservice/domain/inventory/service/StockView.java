
// src/main/java/com/eatcloud/storeservice/domain/inventory/service/StockView.java
package com.eatcloud.storeservice.domain.inventory.service;

import java.util.Objects;

/** 내부 조회용 뷰 모델 (캐시/DB 조회 결과 전달용) */
public class StockView {
    private final int available;
    private final int reserved;

    public StockView(int available, int reserved) {
        this.available = available;
        this.reserved = reserved;
    }

    public int getAvailable() { return available; }
    public int getReserved() { return reserved; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StockView)) return false;
        StockView that = (StockView) o;
        return available == that.available && reserved == that.reserved;
    }

    @Override
    public int hashCode() {
        return Objects.hash(available, reserved);
    }

    @Override
    public String toString() {
        return "StockView{available=" + available + ", reserved=" + reserved + '}';
    }
}
