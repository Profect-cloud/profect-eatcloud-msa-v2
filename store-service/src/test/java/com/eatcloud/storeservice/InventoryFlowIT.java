// src/test/java/.../InventoryFlowIT.java
package com.eatcloud.storeservice;

import com.eatcloud.storeservice.domain.inventory.hotpath.HotPathLuaService;
import com.eatcloud.storeservice.domain.inventory.service.InventoryService;
import com.eatcloud.storeservice.testinfra.InfraContainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class InventoryFlowIT extends InfraContainers {

    @Autowired HotPathLuaService hot;
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;

    @Test
    void reserve_confirm_cancel_end_to_end() {
        UUID menuId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID lineId  = UUID.randomUUID();

        // seed redis & DB (관리자 API 없이 직접)
        hot.seedRedisStock(menuId, 10, 0);
        jdbc.update("""
            INSERT INTO inventory_stock(menu_id, available_qty, reserved_qty, is_unlimited, created_at, updated_at)
            VALUES (?,?,?,?, now(), now())
        """, menuId, 10, 0, false);

        // 1) reserve (핫키 가정: HotKeyDecider가 true를 주도록 테스트 설정 필요하거나, 그냥 cold로 가도 OK)
        inventory.reserve(orderId, lineId, menuId, 3);

        // Outbox 확인 (reserved)
        int reservedCnt = jdbc.queryForObject("""
            SELECT count(*) FROM p_outbox WHERE event_type='stock.reserved' AND payload->>'orderLineId'=? 
        """, Integer.class, lineId.toString());
        assertThat(reservedCnt).isGreaterThanOrEqualTo(1);

        // 2) confirm
        inventory.confirm(lineId);

        int committedCnt = jdbc.queryForObject("""
            SELECT count(*) FROM p_outbox WHERE event_type='stock.committed' AND payload->>'orderLineId'=? 
        """, Integer.class, lineId.toString());
        assertThat(committedCnt).isEqualTo(1);

        // 3) cancelAfterConfirm (보상)
        inventory.cancelAfterConfirm(lineId, "USER_CANCEL");

        int returnedCnt = jdbc.queryForObject("""
            SELECT count(*) FROM p_outbox WHERE event_type='stock.returned' AND payload->>'orderLineId'=? 
        """, Integer.class, lineId.toString());
        assertThat(returnedCnt).isEqualTo(1);

        // 재고 정합성(대략 확인)
        Map<String,Object> row = jdbc.queryForMap("""
            SELECT available_qty, reserved_qty FROM inventory_stock WHERE menu_id=?
        """, menuId);
        int available = ((Number)row.get("available_qty")).intValue();
        int reserved  = ((Number)row.get("reserved_qty")).intValue();
        assertThat(available).isEqualTo(10); // 3 예약→확정→반납까지 하면 원복
        assertThat(reserved).isEqualTo(0);
    }

    @Test
    void reserve_insufficient_emits_event() {
        UUID menuId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID lineId  = UUID.randomUUID();

        hot.seedRedisStock(menuId, 1, 0);
        jdbc.update("""
            INSERT INTO inventory_stock(menu_id, available_qty, reserved_qty, is_unlimited, created_at, updated_at)
            VALUES (?,?,?,?, now(), now())
        """, menuId, 1, 0, false);

        // 5개 요청 → 부족
        inventory.reserve(orderId, lineId, menuId, 5);

        int insufficientCnt = jdbc.queryForObject("""
            SELECT count(*) FROM p_outbox WHERE event_type='stock.insufficient' AND payload->>'orderLineId'=? 
        """, Integer.class, lineId.toString());
        assertThat(insufficientCnt).isEqualTo(1);
    }
}
