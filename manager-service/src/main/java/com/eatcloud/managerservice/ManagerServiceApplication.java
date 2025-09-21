package com.eatcloud.managerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
@SpringBootApplication
@ComponentScan(basePackages = {"com.eatcloud.managerservice", "com.eatcloud.logging"})public class ManagerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ManagerServiceApplication.class, args);
	}

}
