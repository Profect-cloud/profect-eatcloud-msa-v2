package com.eatcloud.paymentservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(
	basePackages = "com.eatcloud",
	repositoryBaseClass = com.eatcloud.autotime.repository.SoftDeleteRepositoryImpl.class
)
public class JpaRepoConfig {

}

