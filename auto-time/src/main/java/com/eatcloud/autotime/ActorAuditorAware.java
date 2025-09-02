package com.eatcloud.autotime;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class ActorAuditorAware implements AuditorAware<String> {
	@Override
	public Optional<String> getCurrentAuditor() {
		var ctx = SecurityContextHolder.getContext();
		if (ctx != null && ctx.getAuthentication() != null && ctx.getAuthentication().isAuthenticated()) {
			var name = ctx.getAuthentication().getName();
			if (name != null && !name.isBlank()) return Optional.of(name);
		}
		var attrs = RequestContextHolder.getRequestAttributes();
		if (attrs instanceof ServletRequestAttributes sra) {
			var actor = sra.getRequest().getHeader("X-Actor-Id");
			if (actor != null && !actor.isBlank()) return Optional.of(actor);
		}
		return Optional.of("system");
	}
}
