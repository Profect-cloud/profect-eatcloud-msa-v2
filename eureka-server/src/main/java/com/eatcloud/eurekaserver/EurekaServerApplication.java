package com.eatcloud.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@ComponentScan(basePackages = {"com.eatcloud.eurekaserver", "com.eatcloud.logging"})@EnableEurekaServer
public class EurekaServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(EurekaServerApplication.class, args);
	}

}
