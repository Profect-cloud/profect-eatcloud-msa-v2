package com.eatcloud.storeservice.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(
            @Value("${redis.url}") String redisUrl
    ) {
        Config cfg = new Config();
        cfg.useSingleServer()
                .setAddress(redisUrl)             // e.g. redis://localhost:6379 (ElastiCache 엔드포인트)
                .setConnectionPoolSize(32)         // >= 24
                .setConnectionMinimumIdleSize(24)  // 기본값과 동일하게 맞춤
                .setIdleConnectionTimeout(10000)
                .setConnectTimeout(10000)
                .setTimeout(10000)
                .setRetryAttempts(2)
                .setRetryInterval(200);
        return Redisson.create(cfg);
    }
}
