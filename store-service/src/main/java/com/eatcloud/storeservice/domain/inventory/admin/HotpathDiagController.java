package com.eatcloud.storeservice.domain.inventory.admin;

// package com.eatcloud.storeservice.domain.inventory.admin;

import com.eatcloud.storeservice.domain.inventory.hotpath.HotPathLuaService;
import com.eatcloud.storeservice.domain.inventory.service.InventoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores/admin/hotpath/diag")
@RequiredArgsConstructor
public class HotpathDiagController {

    private final HotPathLuaService hotpath;
    private final InventoryQueryService queries;

    @GetMapping("/ping")
    public Map<String,Object> ping() {
        return Map.of("ok", true, "now", java.time.Instant.now().toString());
    }

    @GetMapping("/redis/{menuId}")
    public Map<String,Object> redis(@PathVariable UUID menuId) {
        try {
            return Map.of("redis", hotpath.readRedisStock(menuId));
        } catch (Exception e) {
            return Map.of("redisError", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @GetMapping("/db/{menuId}")
    public Map<String,Object> db(@PathVariable UUID menuId) {
        try {
            var v = queries.getStock(menuId);
            return Map.of("db", Map.of("available", v.getAvailable(), "reserved", v.getReserved()));
        } catch (Exception e) {
            return Map.of("dbError", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
