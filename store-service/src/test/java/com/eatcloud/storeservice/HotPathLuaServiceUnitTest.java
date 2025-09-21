// src/test/java/.../HotPathLuaServiceUnitTest.java
package com.eatcloud.storeservice.domain.inventory.hotpath;

import com.eatcloud.storeservice.domain.inventory.entity.InventoryReservation;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryReservationRepository;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryStockRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HotPathLuaServiceUnitTest {

    RedissonClient redisson = mock(RedissonClient.class);
    RScript script = mock(RScript.class);
    InventoryStockRepository stockRepo = mock(InventoryStockRepository.class);
    InventoryReservationRepository resRepo = mock(InventoryReservationRepository.class);

    HotPathLuaService sut;

    UUID orderId = UUID.randomUUID();
    UUID lineId  = UUID.randomUUID();
    UUID menuId  = UUID.randomUUID();

    HotPathLuaServiceUnitTest() {
        when(redisson.getScript(StringCodec.INSTANCE)).thenReturn(script);
        sut = new HotPathLuaService(redisson, stockRepo, resRepo);
    }

    @Test
    void reserve_success_returns_true_and_persists_pending() {
        when(script.eval(any(), anyString(), any(), anyList(), any(), any(), any(), any()))
                .thenReturn(1L); // Lua success
        when(stockRepo.reserve(menuId, 2)).thenReturn(1);
        when(resRepo.findByOrderLineId(lineId)).thenReturn(Optional.empty());

        boolean ok = sut.reserveViaLua(orderId, lineId, menuId, 2);

        assertTrue(ok);
        verify(stockRepo).reserve(menuId, 2);
        verify(resRepo).save(Mockito.argThat((InventoryReservation r) ->
                r.getOrderLineId().equals(lineId) && r.getQty() == 2 && r.getStatus().equals("PENDING")));
    }

    @Test
    void reserve_insufficient_returns_false() {
        when(script.eval(any(), anyString(), any(), anyList(), any(), any(), any(), any()))
                .thenReturn(0L); // insufficient

        boolean ok = sut.reserveViaLua(orderId, lineId, menuId, 5);

        assertFalse(ok);
        verifyNoInteractions(stockRepo);
    }

    @Test
    void reserve_db_fail_rolls_back_and_returns_false() {
        when(script.eval(any(), anyString(), any(), anyList(), any(), any(), any(), any()))
                .thenReturn(1L); // lua ok
        when(stockRepo.reserve(menuId, 3)).thenReturn(0); // DB CAS fail

        boolean ok = sut.reserveViaLua(orderId, lineId, menuId, 3);

        assertFalse(ok);
        // rollback lua 호출(두 번째 eval) 검증
        verify(script, times(2)).eval(any(), anyString(), any(), anyList(), any(), any());
    }
}
