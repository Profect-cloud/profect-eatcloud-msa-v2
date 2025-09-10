package com.eatcloud.storeservice.domain.inventory;

import com.eatcloud.storeservice.domain.inventory.repository.InventoryReservationRepository;
import com.eatcloud.storeservice.domain.inventory.repository.InventoryStockRepository;
import com.eatcloud.storeservice.domain.inventory.service.InventoryService;
import com.eatcloud.storeservice.support.lock.RedisLockExecutor;
import com.eatcloud.storeservice.testsupport.InventoryTestStubs;
import org.junit.jupiter.api.*;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.testcontainers.utility.DockerImageName;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest
@Import(InventoryTestStubs.class)              // Redis 락 스텁 주입
@AutoConfigureTestDatabase(replace = Replace.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class InventoryConcurrencyTest {

    private static final DockerImageName POSTGIS_IMAGE =
            DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(POSTGIS_IMAGE)
            .withDatabaseName("store_db")
            .withUsername("eatcloud_user")
            .withPassword("devpassword123");

    // ✅ Spring Datasource를 Testcontainers로 오버라이드
    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        // 스키마 실행 (db/schema.sql 경로 사용 중이면 지정)
        r.add("spring.sql.init.mode", () -> "always");
        r.add("spring.sql.init.schema-locations", () -> "classpath:db/schema.sql");
        // 외부 의존 비활성화
        r.add("eureka.client.enabled", () -> "false");
        r.add("spring.data.redis.client-type", () -> "none");
        r.add("spring.main.allow-bean-definition-overriding", () -> "true");
    }

    @Autowired InventoryService inventoryService;
    @Autowired InventoryReservationRepository reservationRepo;
    @Autowired InventoryStockRepository stockRepo;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired RedisLockExecutor redisLockExecutor;

    // ✅ 실 RedissonClient 생성 막기 (빈을 Mockito로 대체)
    @MockitoBean
    RedissonClient redissonClient;

    static final UUID MENU_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        // 초기화: 재고 100
        jdbc.update("""
            INSERT INTO inventory_stock(menu_id, available_qty, reserved_qty, is_unlimited, updated_at)
            VALUES (?, ?, 0, false, now())
            ON CONFLICT (menu_id) DO UPDATE
            SET available_qty = EXCLUDED.available_qty,
                reserved_qty = 0,
                is_unlimited = false,
                updated_at = now()
        """, MENU_ID, 100);

        jdbc.update("DELETE FROM inventory_reservations WHERE menu_id = ?", MENU_ID);
    }

    @Test
    @DisplayName("동시 200 예약: 성공 100, 실패 100 → 오버셀 0")
    void concurrent_reserve_no_oversell() throws Exception {
        int totalTry = 200;

        ExecutorService pool = Executors.newFixedThreadPool(50);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < totalTry; i++) {
            UUID orderId = UUID.randomUUID();
            UUID orderLineId = UUID.randomUUID();
            tasks.add(() -> {
                try {
                    inventoryService.reserve(orderId, orderLineId, MENU_ID, 1);
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            });
        }

        List<Future<Boolean>> results = pool.invokeAll(tasks);
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        long success = results.stream().filter(f -> {
            try { return Boolean.TRUE.equals(f.get()); } catch (Exception e) { return false; }
        }).count();

        assertThat(success).isEqualTo(100);

        var row = jdbc.queryForMap("SELECT available_qty, reserved_qty FROM inventory_stock WHERE menu_id = ?", MENU_ID);
        assertThat(row.get("available_qty")).isEqualTo(0);
        assertThat(row.get("reserved_qty")).isEqualTo(100);
    }

    @Test
    @DisplayName("멱등성: 같은 orderLineId 두 번 → 1회만 반영")
    void idempotency_same_orderLine_twice() {
        UUID orderId = UUID.randomUUID();
        UUID orderLineId = UUID.randomUUID();

        inventoryService.reserve(orderId, orderLineId, MENU_ID, 1);
        inventoryService.reserve(orderId, orderLineId, MENU_ID, 1); // no-op

        long cnt = reservationRepo.findAll().stream()
                .filter(r -> orderLineId.equals(r.getOrderLineId()))
                .count();
        assertThat(cnt).isEqualTo(1);

        var row = jdbc.queryForMap("SELECT available_qty, reserved_qty FROM inventory_stock WHERE menu_id = ?", MENU_ID);
        assertThat(row.get("available_qty")).isEqualTo(99);
        assertThat(row.get("reserved_qty")).isEqualTo(1);
    }

    @Test
    @DisplayName("락 타임아웃: LockTimeoutException 발생")
    void lock_timeout_returns_exception() {
        ((InventoryTestStubs.LockStub) redisLockExecutor).setForceTimeout(true);

        UUID orderId = UUID.randomUUID();
        UUID orderLineId = UUID.randomUUID();

        assertThatThrownBy(() -> inventoryService.reserve(orderId, orderLineId, MENU_ID, 1))
                .isInstanceOf(RedisLockExecutor.LockTimeoutException.class);
    }
}
