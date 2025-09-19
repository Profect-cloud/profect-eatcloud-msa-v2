// src/test/java/.../InfraContainers.java
package com.eatcloud.storeservice.testinfra;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;

public abstract class InfraContainers {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @BeforeAll
    static void startAll() {
        POSTGRES.start();
        REDIS.start();
        System.setProperty("spring.datasource.url", POSTGRES.getJdbcUrl());
        System.setProperty("spring.datasource.username", POSTGRES.getUsername());
        System.setProperty("spring.datasource.password", POSTGRES.getPassword());

        // redisson 연결용 (예: spring.redisson.address=redis://host:port)
        System.setProperty("spring.redis.host", REDIS.getHost());
        System.setProperty("spring.redis.port", REDIS.getMappedPort(6379).toString());
        System.setProperty("redisson.address",
                "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));

        // 퍼블리셔는 돌려도 되고, 테스트에서 수동으로 outbox만 확인해도 OK
        System.setProperty("inventory.outbox.publisher.interval-ms", "86400000"); // 테스트에서 자동 발행 막기
    }

    @AfterAll
    static void stopAll() {
        REDIS.stop();
        POSTGRES.stop();
    }
}
