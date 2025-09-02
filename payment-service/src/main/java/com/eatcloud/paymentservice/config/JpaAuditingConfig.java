package com.eatcloud.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.eatcloud.autotime.ActorAuditorAware;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "actorAuditorAware", modifyOnCreate = true)
public class JpaAuditingConfig {

	@Bean
	public AuditorAware<String> actorAuditorAware() {
		return new ActorAuditorAware();
	}
}
