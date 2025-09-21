package com.eatcloud.storeservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@ComponentScan(basePackages = {"com.eatcloud.storeservice", "com.eatcloud.logging"})public class StoreServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(StoreServiceApplication.class, args);
	}

}
