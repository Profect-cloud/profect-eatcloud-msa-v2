package com.eatcloud.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication(exclude = {RedisRepositoriesAutoConfiguration.class})
@ComponentScan(basePackages = {"com.eatcloud.orderservice", "com.eatcloud.logging"})
@EnableRetry
public class OrderServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}
}