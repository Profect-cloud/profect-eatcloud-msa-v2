package com.eatcloud.adminservice.ports;

import java.util.UUID;

public interface StoreAdminPort {
    UUID createStore(CreateStoreCommand cmd);
    void closeStore(UUID storeId, CloseStoreCommand cmd); // storeId는 PathVariable로
}

