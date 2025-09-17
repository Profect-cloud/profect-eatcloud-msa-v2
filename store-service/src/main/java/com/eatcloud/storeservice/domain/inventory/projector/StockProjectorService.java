// package: com.eatcloud.storeservice.domain.inventory.projector
package com.eatcloud.storeservice.domain.inventory.projector;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockProjectorService {

    private final StockProjectionRepository projectionRepo;
    private final StockProjProcessedRepository processedRepo;

    @Transactional
    public void apply(StockEventEnvelope evt) {
        if (evt == null || evt.getId() == null) {
            log.warn("[Projector] skip: null evt or id");
            return;
        }
        if (processedRepo.existsById(evt.getId())) {
            // 멱등
            return;
        }

        UUID menuId = evt.getAggregateId();
        if (menuId == null) {
            log.warn("[Projector] skip: aggregateId (menuId) is null. evtId={}", evt.getId());
            // 멱등표시는 남겨 중복 재처리 방지
            processedRepo.save(StockProjProcessed.builder()
                    .eventId(evt.getId()).processedAt(LocalDateTime.now()).build());
            return;
        }

        JsonNode p = evt.getPayload();

        // 읽기모델 로드/초기화
        StockProjectionEntity view = projectionRepo.findById(menuId).orElse(
                StockProjectionEntity.builder()
                        .menuId(menuId)
                        .avail(0)
                        .reserved(0)
                        .updatedAt(LocalDateTime.now())
                        .build()
        );

        String type = evt.getEventType();

        // qty / delta 유연 매핑
        int qty = extractQty(p);         // reserved/committed/released/returned/insufficient
        int delta = extractDelta(p);     // adjusted

        switch (type) {
            case "stock.reserved" -> {
                view.setAvail(view.getAvail() - qty);
                view.setReserved(view.getReserved() + qty);
            }
            case "stock.committed" -> {
                // 예약분 확정: avail은 이미 감소된 상태, reserved만 감소
                view.setReserved(view.getReserved() - qty);
            }
            case "stock.released", "stock.returned", "stock.canceled" -> {
                // 예약 취소/반납: avail +, reserved -
                view.setAvail(view.getAvail() + qty);
                view.setReserved(view.getReserved() - qty);
            }
            case "stock.adjusted" -> {
                // 관리자 가감(입고/차감). delta는 ±값
                view.setAvail(view.getAvail() + delta);
            }
            case "stock.insufficient" -> {
                // 수량 변화 없음(상태 알림용)
                log.debug("[Projector] insufficient event applied (no-op) menuId={} reqQty={}", menuId, qty);
            }
            default -> {
                log.warn("[Projector] unknown type={}, evtId={} -> skip", type, evt.getId());
                processedRepo.save(StockProjProcessed.builder()
                        .eventId(evt.getId()).processedAt(LocalDateTime.now()).build());
                return;
            }
        }

        // 음수 방지(보수적 클램프)
        if (view.getAvail() < 0) view.setAvail(0);
        if (view.getReserved() < 0) view.setReserved(0);

        projectionRepo.save(view);
        processedRepo.save(StockProjProcessed.builder()
                .eventId(evt.getId()).processedAt(LocalDateTime.now()).build());

        log.info("Projection updated: menuId={} type={} avail={} reserved={}",
                menuId, type, view.getAvail(), view.getReserved());
    }

    /** qty는 이벤트 종류에 따라 키가 다를 수 있어 관대하게 추출 */
    private int extractQty(JsonNode p) {
        if (p == null) return 0;
        if (p.has("qty"))          return safeInt(p.get("qty"));
        if (p.has("quantity"))     return safeInt(p.get("quantity"));
        if (p.has("requestedQty")) return safeInt(p.get("requestedQty"));
        return 0;
    }

    /** adjusted 전용 delta(±). 없으면 0 */
    private int extractDelta(JsonNode p) {
        if (p == null) return 0;
        if (p.has("delta")) return safeInt(p.get("delta"));
        // 혹시 delta 대신 quantity로 오는 케이스도 방어
        if (p.has("quantity")) return safeInt(p.get("quantity"));
        return 0;
    }

    private int safeInt(JsonNode n) {
        try {
            return n == null ? 0 : n.asInt();
        } catch (Exception e) {
            return 0;
        }
    }
}
