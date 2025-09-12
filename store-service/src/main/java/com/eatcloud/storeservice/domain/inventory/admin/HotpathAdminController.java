package com.eatcloud.storeservice.domain.inventory.admin;

import com.eatcloud.storeservice.domain.inventory.hot.HotKeyDecider;
import com.eatcloud.storeservice.domain.inventory.hotpath.HotPathLuaService;
import com.eatcloud.storeservice.domain.inventory.service.InventoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores/admin/hotpath")
@RequiredArgsConstructor
public class HotpathAdminController {

    private final HotPathLuaService hotpath;
    private final HotKeyDecider hot;
    private final InventoryQueryService queries; // DB에서 현재 재고 읽어오는 기존 서비스

    /** DB 수치를 읽어 Redis 키(inv:{menuId}:avail/reserved)로 시드 */
    @PostMapping("/seed/{menuId}")
    public void seed(@PathVariable UUID menuId) {
        var v = queries.getStock(menuId); // available/reserved를 리턴하는 메서드여야 함
        hotpath.seedRedisStock(menuId, v.available(), v.reserved());
    }

    /** 핫키 on/off 토글 */
    @PostMapping("/toggle/{menuId}/on")
    public void markHot(@PathVariable UUID menuId) { hot.markHot(menuId); }

    @PostMapping("/toggle/{menuId}/off")
    public void unmarkHot(@PathVariable UUID menuId) { hot.unmarkHot(menuId); }

    /** Redis 현재 값 확인(점검용) */
    @GetMapping("/cache/{menuId}")
    public Map<String, Integer> readCache(@PathVariable UUID menuId) {
        return hotpath.readRedisStock(menuId);
    }

    /** DB→Redis 강제 동기화(캐시 리프레시) */
    @PostMapping("/refresh/{menuId}")
    public void refreshFromDb(@PathVariable UUID menuId) {
        var v = queries.getStock(menuId);
        hotpath.seedRedisStock(menuId, v.available(), v.reserved());
    }
}
