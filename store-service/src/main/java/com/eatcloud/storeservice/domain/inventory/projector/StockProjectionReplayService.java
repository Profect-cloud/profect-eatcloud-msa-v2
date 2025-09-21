// package: com.eatcloud.storeservice.domain.inventory.projector
package com.eatcloud.storeservice.domain.inventory.projector;

import com.eatcloud.storeservice.domain.inventory.event.StockEventEntity;
import com.eatcloud.storeservice.domain.inventory.event.StockEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockProjectionReplayService {

    private final StockEventRepository eventRepo;
    private final StockProjectionRepository projectionRepo;
    private final StockProjProcessedRepository processedRepo;

    /** 특정 메뉴의 프로젝션을 이벤트만으로 재구축 */
    @Transactional
    public void rebuildForMenu(UUID menuId) {
        var events = eventRepo.findAll().stream()
                .filter(e -> e.getMenuId().equals(menuId))
                .sorted(Comparator.comparing(StockEventEntity::getCreatedAt))
                .toList();

        int avail = 0, reserved = 0;

        for (var e : events) {
            switch (e.getEventType()) {
                case "stock.reserved"   -> { avail -= e.getQuantity(); reserved += e.getQuantity(); }
                case "stock.committed"  -> { reserved -= e.getQuantity(); }
                case "stock.released", "stock.returned", "stock.canceled" -> { avail += e.getQuantity(); reserved -= e.getQuantity(); }
                case "stock.adjusted"   -> {
                    // StockEventEntity.quantity 필드가 delta 용도로도 쓰인다면(+/-)
                    avail += e.getQuantity();
                }
                case "stock.insufficient" -> {}
            }
            if (avail < 0) avail = 0;
            if (reserved < 0) reserved = 0;
        }

        var view = projectionRepo.findById(menuId).orElse(
                StockProjectionEntity.builder().menuId(menuId).avail(0).reserved(0).build()
        );
        view.setAvail(avail);
        view.setReserved(reserved);
        projectionRepo.save(view);

        log.info("Rebuilt projection for menuId={} -> avail={}, reserved={}", menuId, avail, reserved);
    }
}
