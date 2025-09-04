package com.eatcloud.storeservice.domain.menu.service;

import com.eatcloud.storeservice.domain.menu.entity.Menu;
import com.eatcloud.storeservice.domain.menu.entity.StockLog;
import com.eatcloud.storeservice.domain.menu.repository.MenuRepository;
import com.eatcloud.storeservice.domain.menu.repository.StockLogRepository;
import com.eatcloud.storeservice.internal.lock.MenuLock;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class MenuStockService {

    private final MenuRepository menuRepo;
    private final StockLogRepository logRepo;
    private final MenuLock menuLock;

    public void reserve(UUID orderId, UUID lineId, UUID menuId, int qty) {
        menuLock.withLock(menuId, () -> {
            Menu m = menuRepo.findById(menuId).orElseThrow();

            if (Boolean.TRUE.equals(m.getIsUnlimited())) {
                logRepo.save(StockLog.of("RESERVE", menuId, orderId, lineId, 0, "UNLIMITED"));
                return;
            }

            int updated = menuRepo.tryReserve(menuId, qty);
            if (updated == 0) throw new RuntimeException("INSUFFICIENT_STOCK");

            logRepo.save(StockLog.of("RESERVE", menuId, orderId, lineId, -qty, null));
        });
    }

    public void confirm(UUID orderId, UUID lineId, UUID menuId, int qty) {
        // 예약 시 이미 차감됨 → 수량 변화 없음
        logRepo.save(StockLog.of("CONFIRM", menuId, orderId, lineId, 0, null));
    }

    public void cancel(UUID orderId, UUID lineId, UUID menuId, int qty, String reason) {
        menuLock.withLock(menuId, () -> {
            Menu m = menuRepo.findById(menuId).orElseThrow();

            if (Boolean.TRUE.equals(m.getIsUnlimited())) {
                logRepo.save(StockLog.of("CANCEL", menuId, orderId, lineId, 0, "UNLIMITED"));
                return;
            }

            int updated = menuRepo.tryRestore(menuId, qty);
            if (updated == 0) throw new RuntimeException("RESTORE_FAILED");

            logRepo.save(StockLog.of("CANCEL", menuId, orderId, lineId, +qty, reason));
        });
    }
}
