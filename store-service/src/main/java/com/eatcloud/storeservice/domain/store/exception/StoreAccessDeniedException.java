package com.eatcloud.storeservice.domain.store.exception;

public class StoreAccessDeniedException extends RuntimeException {
    private final String managerId;
    private final String storeId;

    public StoreAccessDeniedException(String managerId, String storeId) {
        super(String.format("Manager %s does not have access to store %s", managerId, storeId));
        this.managerId = managerId;
        this.storeId = storeId;
    }

    public String getManagerId() { return managerId; }
    public String getStoreId() { return storeId; }
}