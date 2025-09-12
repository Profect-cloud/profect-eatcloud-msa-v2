package com.eatcloud.storeservice.domain.inventory.hotpath;

import com.eatcloud.storeservice.domain.inventory.StockEvents;
import com.eatcloud.storeservice.domain.inventory.entity.InventoryReservation;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryReservationRepository;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryStockRepository;
import com.eatcloud.storeservice.domain.inventory.service.InsufficientStockException;
import com.eatcloud.storeservice.domain.outbox.service.OutboxAppender;
import com.fasterxml.classmate.AnnotationOverrides;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotPathLuaService {

    private final RedissonClient redisson;
    private final InventoryStockRepository stockRepo;
    private final InventoryReservationRepository resRepo;
    private final OutboxAppender outboxAppender;

    // KEYS[1]=avail, KEYS[2]=reserved, KEYS[3]=orders(set)
    // ARGV[1]=menuId, ARGV[2]=orderId, ARGV[3]=orderLineId, ARGV[4]=qty
    private static final String LUA_RESERVE = """
        local availKey   = KEYS[1]
        local reservedKey= KEYS[2]
        local ordersKey  = KEYS[3]

        local menuId      = ARGV[1]
        local orderId     = ARGV[2]
        local orderLineId = ARGV[3]
        local qty         = tonumber(ARGV[4])

        -- idempotency
        if redis.call('SISMEMBER', ordersKey, orderLineId) == 1 then
          return 2     -- 이미 처리됨
        end

        local avail = tonumber(redis.call('GET', availKey) or '0')
        if avail < qty then
          return 0     -- 재고부족
        end

        redis.call('DECRBY', availKey, qty)
        redis.call('INCRBY', reservedKey, qty)
        redis.call('SADD', ordersKey, orderLineId)

        return 1       -- 성공
        """;

    // 보상(롤백): idempotency set 제거 + 수량 복구
    private static final String LUA_ROLLBACK = """
        local availKey   = KEYS[1]
        local reservedKey= KEYS[2]
        local ordersKey  = KEYS[3]

        local orderLineId = ARGV[1]
        local qty         = tonumber(ARGV[2])

        if redis.call('SISMEMBER', ordersKey, orderLineId) == 1 then
          redis.call('SREM', ordersKey, orderLineId)
        end
        redis.call('INCRBY', availKey, qty)
        redis.call('DECRBY', reservedKey, qty)
        return 1
        """;

    private List<String> keys(UUID menuId) {
        String prefix = "inv:" + menuId;
        return List.of(prefix + ":avail", prefix + ":reserved", prefix + ":orders");
    }

    /**
     * 핫키 경로: Redis Lua로 먼저 원자 차감 → DB CAS 성공 시 확정,
     * DB 실패하면 Lua 롤백.
     */
    @Transactional
    public void reserveViaLua(UUID orderId, UUID orderLineId, UUID menuId, int qty) {
        // 1) Lua로 선차감
        RScript script = redisson.getScript(StringCodec.INSTANCE);

        Long r = script.eval(
                RScript.Mode.READ_WRITE,
                LUA_RESERVE,                           // 2) script
                RScript.ReturnType.INTEGER,            // 3) return type
                new java.util.ArrayList<>(keys(menuId)), // 4) KEYS as List<Object>
                menuId.toString(),                     // 5) ARGV...
                orderId.toString(),
                orderLineId.toString(),
                String.valueOf(qty)                    // String으로 전달 권장
        );


        if (r == null) r = -1L;
        if (r == 0) { // 부족
            throw new InsufficientStockException();
        }
        if (r == 2) { // 멱등: 이미 처리된 orderLineId
            log.debug("[HOTPATH] idempotent hit. orderLineId={}", orderLineId);
            return;
        }
        if (r != 1) {
            log.warn("[HOTPATH] unexpected lua return: {}", r);
            throw new IllegalStateException("lua-unexpected");
        }

        // 2) DB 반영: CAS (available-qty, reserved+qty)
        //    실패 시 Lua 롤백
        int updated = stockRepo.reserve(menuId, qty);
        if (updated == 0) {
            log.warn("[HOTPATH] DB CAS failed. rolling back Lua. menuId={}, qty={}", menuId, qty);
            rollbackLua(orderLineId, menuId, qty);
            throw new InsufficientStockException(); // 혹은 503 반환 분기
        }

        // 3) 예약 row 기록 (PENDING)
        var reservation = InventoryReservation.builder()
                .reservationId(UUID.randomUUID())
                .menuId(menuId)
                .orderId(orderId)
                .orderLineId(orderLineId)
                .qty(qty)
                .status("PENDING")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .createdAt(LocalDateTime.now())
                .build();
        resRepo.findByOrderLineId(orderLineId).ifPresentOrElse(
                it -> log.debug("[HOTPATH] reservation already exists (idempotent). {}", orderLineId),
                () -> resRepo.save(reservation)
        );

        // 4) Outbox 이벤트
        outboxAppender.append(StockEvents.RESERVED, menuId, Map.of(
                "menuId", menuId, "orderId", orderId, "orderLineId", orderLineId,
                "qty", qty, "occurredAt", LocalDateTime.now(), "eventVersion", 1
        ));
    }

    private void rollbackLua(UUID orderLineId, UUID menuId, int qty) {
        try {
            redisson.getScript(StringCodec.INSTANCE).eval(
                    RScript.Mode.READ_WRITE,
                    LUA_ROLLBACK,
                    RScript.ReturnType.INTEGER,
                    new java.util.ArrayList<>(keys(menuId)),
                    orderLineId.toString(),
                    String.valueOf(qty)
            );
        } catch (Exception e) {
            log.error("[HOTPATH] rollback lua failed! menuId={}, orderLineId={}, qty={}, err={}",
                    menuId, orderLineId, qty, e.toString());
        }
    }

    /** 초기 sync: Redis에 재고 시드(필요 시 Admin에서 호출) */
    public void seedRedisStock(UUID menuId, int available, int reserved) {
        String prefix = "inv:" + menuId;
        redisson.getBucket(prefix + ":avail", StringCodec.INSTANCE).set(Integer.toString(available));
        redisson.getBucket(prefix + ":reserved", StringCodec.INSTANCE).set(Integer.toString(reserved));
        // orders(set)은 그대로 두면 됨
    }

    // Redis에 현재 값 조회(점검용)
    public Map<String, Integer> readRedisStock(UUID menuId) {
        String p = "inv:" + menuId;
        var a = redisson.getBucket(p + ":avail", StringCodec.INSTANCE).get();
        var r = redisson.getBucket(p + ":reserved", StringCodec.INSTANCE).get();
        int avail = (a == null) ? 0 : Integer.parseInt(a.toString());
        int resvd = (r == null) ? 0 : Integer.parseInt(r.toString());
        return Map.of("available", avail, "reserved", resvd);
    }

}
