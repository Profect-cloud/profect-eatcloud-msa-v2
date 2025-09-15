package com.eatcloud.storeservice.domain.inventory;

public final class StockEvents {
    private StockEvents() {}

    public static final String RESERVED = "stock.reserved";
    public static final String RELEASED = "stock.released";
    public static final String ADJUSTED = "stock.adjusted";
    public static final String CONFIRMED = "stock.confirmed";
}