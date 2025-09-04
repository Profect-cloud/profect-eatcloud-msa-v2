package com.eatcloud.storeservice.domain.menu.controller;

import com.eatcloud.storeservice.domain.menu.dto.InventoryCancelRequest;
import com.eatcloud.storeservice.domain.menu.dto.InventoryConfirmRequest;
import com.eatcloud.storeservice.domain.menu.dto.InventoryReserveRequest;
import com.eatcloud.storeservice.domain.menu.service.MenuStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory")
public class MenuStockController {

    private final MenuStockService svc;

    @PostMapping("/reserve")
    public void reserve(@RequestBody InventoryReserveRequest r) {
        svc.reserve(r.getOrderId(), r.getOrderLineId(), r.getMenuId(), r.getQuantity());
    }

    @PostMapping("/confirm")
    public void confirm(@RequestBody InventoryConfirmRequest r) {
        svc.confirm(r.getOrderId(), r.getOrderLineId(), r.getMenuId(), r.getQuantity());
    }

    @PostMapping("/cancel")
    public void cancel(@RequestBody InventoryCancelRequest r) {
        svc.cancel(r.getOrderId(), r.getOrderLineId(), r.getMenuId(), r.getQuantity(), r.getReason());
    }
}
