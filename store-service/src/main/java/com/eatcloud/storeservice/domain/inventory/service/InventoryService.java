// InventoryService.java
package com.eatcloud.storeservice.domain.inventory.service;

import java.util.UUID;

public interface InventoryService {

    void reserve(UUID orderId, UUID orderLineId, UUID menuId, int qty);

    void confirm(UUID orderLineId);

    void cancel(UUID orderLineId, String reason);

    void adjust(UUID menuId, int delta); // 관리자 증감 (+/-)
}
