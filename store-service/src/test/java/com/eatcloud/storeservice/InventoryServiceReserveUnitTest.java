// src/test/java/.../InventoryServiceReserveUnitTest.java
package com.eatcloud.storeservice.domain.inventory.service;

import com.eatcloud.storeservice.domain.inventory.hot.HotKeyDecider;
import com.eatcloud.storeservice.domain.inventory.hotpath.HotPathLuaService;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryReservationRepository;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryStockRepository;
import com.eatcloud.storeservice.domain.outbox.service.OutboxAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryServiceReserveUnitTest {

    InventoryStockRepository stockRepo = mock(InventoryStockRepository.class);
    InventoryReservationRepository resRepo = mock(InventoryReservationRepository.class);
    OutboxAppender outbox = mock(OutboxAppender.class);
    RedisLockExecutor locks = mock(RedisLockExecutor.class, RETURNS_SELF); // if needed, else use a test double
    HotKeyDecider decider = mock(HotKeyDecider.class);
    HotPathLuaService lua = mock(HotPathLuaService.class);
    org.redisson.api.RedissonClient redisson = mock(org.redisson.api.RedissonClient.class);

    InventoryServiceImpl sut;

    UUID orderId = UUID.randomUUID();
    UUID lineId = UUID.randomUUID();
    UUID menuId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        sut = new InventoryServiceImpl(stockRepo, resRepo, outbox, locks, decider, lua, redisson);
    }

    @Test
    void hot_success_emits_reserved() {
        when(decider.isHot(menuId)).thenReturn(true);
        when(lua.reserveViaLua(orderId, lineId, menuId, 2)).thenReturn(true);

        sut.reserve(orderId, lineId, menuId, 2);

        verify(outbox).append(eq("stock.reserved"), eq("INVENTORY_ITEM"), eq(menuId), anyMap(), anyMap());
        verifyNoMoreInteractions(outbox);
    }

    @Test
    void hot_insufficient_emits_insufficient() {
        when(decider.isHot(menuId)).thenReturn(true);
        when(lua.reserveViaLua(orderId, lineId, menuId, 5)).thenReturn(false);

        sut.reserve(orderId, lineId, menuId, 5);

        verify(outbox).append(eq("stock.insufficient"), eq("INVENTORY_ITEM"), eq(menuId), anyMap(), anyMap());
    }

    @Test
    void cold_success_emits_reserved() {
        when(decider.isHot(menuId)).thenReturn(false);
        when(resRepo.findByOrderLineId(lineId)).thenReturn(Optional.empty());
        when(stockRepo.reserve(menuId, 1)).thenReturn(1);

        // lock runner가 Supplier를 바로 실행했다고 가정하는 테스트 더블 필요
        // 실제 RedisLockExecutor가 복잡하면 별도 테스트 더블 구현 권장

        sut.reserve(orderId, lineId, menuId, 1);

        verify(outbox).append(eq("stock.reserved"), eq("INVENTORY_ITEM"), eq(menuId), anyMap(), anyMap());
    }
}
