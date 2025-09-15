package com.eatcloud.storeservice.domain.inventory.controller;

import com.eatcloud.storeservice.domain.inventory.dto.request.AdjustRequestDto;
import com.eatcloud.storeservice.domain.inventory.dto.request.CancelRequestDto;
import com.eatcloud.storeservice.domain.inventory.dto.request.ConfirmRequestDto;
import com.eatcloud.storeservice.domain.inventory.dto.request.ReserveRequestDto;
import com.eatcloud.storeservice.domain.inventory.dto.response.StockResponseDto;
import com.eatcloud.storeservice.domain.inventory.service.InventoryQueryService;
import com.eatcloud.storeservice.domain.inventory.service.InventoryService;
import com.eatcloud.storeservice.support.lock.RedisLockExecutor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stores/inventory")
public class MenuStockController {

    private final InventoryService inventoryService;
    private final InventoryQueryService queryService;

    @PostMapping("/reserve")
    @ResponseStatus(HttpStatus.OK)
    public void reserve(@Valid @RequestBody ReserveRequestDto req) {
        inventoryService.reserve(req.getOrderId(), req.getOrderLineId(), req.getMenuId(), req.getQty());
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.OK)
    public void confirm(@Valid @RequestBody ConfirmRequestDto req) {
        inventoryService.confirm(req.getOrderLineId());
    }

    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.OK)
    public void cancel(@Valid @RequestBody CancelRequestDto req) {
        inventoryService.cancel(req.getOrderLineId(), req.getReason());
    }

    @PostMapping("/adjust")
    @ResponseStatus(HttpStatus.OK)
    public void adjust(@Valid @RequestBody AdjustRequestDto req) {
        inventoryService.adjust(req.getMenuId(), req.getDelta());
    }

    // 조회 (CQRS Read 모델: 캐시 우선)
    @GetMapping("/{menuId}")
    public StockResponseDto get(@PathVariable("menuId") UUID menuId) {
        var v = queryService.getStock(menuId);
        return new StockResponseDto(v.getAvailable(), v.getReserved());
    }

    @PostMapping("/cancel-after-confirm")
    @ResponseStatus(HttpStatus.OK)
    public void cancelAfterConfirm(@Valid @RequestBody CancelRequestDto req) {
        inventoryService.cancelAfterConfirm(req.getOrderLineId(), req.getReason());
    }


    // (선택) 헬스 체크용 핑
    @GetMapping("/ping")
    public String ping() {
        return "ok";
    }

    // --- 간단 예외 매핑 ---
    @ResponseStatus(HttpStatus.CONFLICT) // 409
    @ExceptionHandler({ com.eatcloud.storeservice.domain.inventory.service.InventoryServiceImpl.InsufficientStockException.class })
    public void onInsufficient() {}

    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE) // 503
    @ExceptionHandler({ RedisLockExecutor.LockTimeoutException.class })
    public void onLockTimeout() {}
}

