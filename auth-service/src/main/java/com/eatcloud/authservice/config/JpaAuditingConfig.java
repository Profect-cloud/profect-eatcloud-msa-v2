package com.eatcloud.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "actorAuditorAware", modifyOnCreate = true)
public class JpaAuditingConfig {

	@Bean
	public AuditorAware<String> actorAuditorAware() {
		return new com.eatcloud.autotime.ActorAuditorAware();
	}
}
