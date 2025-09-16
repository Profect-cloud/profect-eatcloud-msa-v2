package com.eatcloud.storeservice.domain.inventory.hotpath;

import com.eatcloud.storeservice.domain.inventory.entity.InventoryReservation;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryReservationRepository;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryStockRepository;
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
          return 2     -- already processed (idempotent success)
        end

        local avail = tonumber(redis.call('GET', availKey) or '0')
        if avail < qty then
          return 0     -- insufficient
        end

        redis.call('DECRBY', availKey, qty)
        redis.call('INCRBY', reservedKey, qty)
        redis.call('SADD', ordersKey, orderLineId)

        return 1       -- success
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
     * 핫키 경로:
     * 1) Redis Lua로 원자 차감
     * 2) DB CAS로 확정, DB 실패 시 Lua 롤백
     *
     * @return true  = 예약 성공(or 멱등 성공)
     *         false = 부족/롤백 등으로 예약 실패
     */
    @Transactional
    public boolean reserveViaLua(UUID orderId, UUID orderLineId, UUID menuId, int qty) {
        // 1) Lua 선차감
        RScript script = redisson.getScript(StringCodec.INSTANCE);
        Long r = script.eval(
                RScript.Mode.READ_WRITE,
                LUA_RESERVE,
                RScript.ReturnType.INTEGER,
                new java.util.ArrayList<>(keys(menuId)),
                menuId.toString(),
                orderId.toString(),
                orderLineId.toString(),
                String.valueOf(qty)
        );
        if (r == null) r = -1L;

        if (r == 0) {           // 부족
            return false;
        }
        if (r == 2) {           // 멱등(이미 처리) -> 성공으로 간주
            log.debug("[HOTPATH] idempotent hit. orderLineId={}", orderLineId);
            return true;
        }
        if (r != 1) {           // 예기치 못한 반환
            log.warn("[HOTPATH] unexpected lua return: {}", r);
            return false;
        }

        // 2) DB 반영(CAS). 실패 시 Lua 롤백하고 실패 반환
        int updated = stockRepo.reserve(menuId, qty);
        if (updated == 0) {
            log.warn("[HOTPATH] DB CAS failed. rolling back Lua. menuId={}, qty={}", menuId, qty);
            rollbackLua(orderLineId, menuId, qty);
            return false;
        }

        // 3) 예약 row 기록(PENDING, 멱등)
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

        // ✅ 여기서는 Outbox 이벤트를 발행하지 않는다.
        //    상위 InventoryServiceImpl.reserve()가 reserved/insufficient를 단일 책임으로 발행.

        return true;
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
