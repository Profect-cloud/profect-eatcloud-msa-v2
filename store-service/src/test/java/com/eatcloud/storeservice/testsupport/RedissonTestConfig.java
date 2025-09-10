//package com.eatcloud.storeservice.testsupport;
//
//import org.redisson.Redisson;
//import org.redisson.api.RedissonClient;
//import org.redisson.config.Config;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.test.context.TestConfiguration;
//import org.springframework.context.annotation.Bean;
//
//@TestConfiguration
//public class RedissonTestConfig {
//
//    @Bean
//    public RedissonClient redissonClient(
//            @Value("${spring.redis.host}") String host,
//            @Value("${spring.redis.port}") int port
//    ) {
//        Config cfg = new Config();
//        cfg.useSingleServer().setAddress("redis://" + host + ":" + port);
//        return Redisson.create(cfg);
//    }
//}
