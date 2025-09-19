package com.eatcloud.storeservice.domain.inventory.projector;


import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/stores/inventory/projection")
@RequiredArgsConstructor
public class StockProjectionController {

    private final StockProjectionRepository projectionRepo;
    private final StockProjectionReplayService replayService;

    @GetMapping("/{menuId}")
    public Map<String, Object> getOne(@PathVariable UUID menuId) {
        var view = projectionRepo.findById(menuId).orElse(null);
        if (view == null) {
            return Map.of("menuId", menuId, "avail", 0, "reserved", 0, "found", false);
        }
        return Map.of(
                "menuId", view.getMenuId(),
                "avail", view.getAvail(),
                "reserved", view.getReserved(),
                "updatedAt", view.getUpdatedAt(),
                "found", true
        );
    }

    @PostMapping("/rebuild/{menuId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> rebuild(@PathVariable UUID menuId) {
        replayService.rebuildForMenu(menuId);
        return Map.of("menuId", menuId, "status", "rebuilt");
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "where", "projection");
    }

}
