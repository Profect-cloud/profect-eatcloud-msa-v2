package com.eatcloud.apigateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class GatewayConfig {

	@Bean
	public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
		log.info("🚀 Configuring Gateway Routes...");

		return builder.routes()
			.route("auth-service", r -> r
				.path("/api/v1/auth/**")
				.filters(f -> f
					.addRequestHeader("X-Service-Name", "auth-service"))
				.uri("lb://auth-service"))

			.route("customer-service", r -> r
				.path("/api/v1/customers/**")
				.filters(f -> f
					.addRequestHeader("X-Service-Name", "customer-service"))
				.uri("lb://customer-service"))

			.route("admin-service", r -> r
				.path("/api/v1/admin/**")
				.filters(f -> f
					.addRequestHeader("X-Service-Name", "admin-service"))
				.uri("lb://admin-service"))

			.route("manager-service", r -> r
				.path("/api/v1/manager/**")
				.filters(f -> f
					.addRequestHeader("X-Service-Name", "manager-service"))
				.uri("lb://manager-service"))

			// Store Service Routes
			.route("store-service", r -> r
				.path("/api/v1/stores/**")
				.filters(f -> f
					.addRequestHeader("X-Service-Name", "store-service"))
				.uri("lb://store-service"))

			.route("order-service", r -> r
				.path("/api/v1/orders/**")
				.filters(f -> f
					.addRequestHeader("X-Service-Name", "order-service"))
				.uri("lb://order-service"))

			.route("payment-service", r -> r
				.path("/api/v1/payments/**")
				.filters(f -> f
					.addRequestHeader("X-Service-Name", "payment-service"))
				.uri("lb://payment-service"))

			.route("payment-service-views", r -> r
				.path("/payments/**")
				.filters(f -> f
					.addRequestHeader("X-Service-Name", "payment-service"))
				.uri("lb://payment-service"))

			.route("payment-service-callbacks", r -> r
				.path("/api/v1/payment/**")
				.filters(f -> f
					.addRequestHeader("X-Service-Name", "payment-service"))
				.uri("lb://payment-service"))

			.route("auth-service-swagger", r -> r
				.path("/auth-service/**")
				.filters(f -> f
					.rewritePath("/auth-service/(?<segment>.*)", "/${segment}")
					.addRequestHeader("X-Forwarded-Prefix", "/auth-service")
					.addRequestHeader("X-Service-Name", "auth-service"))
				.uri("lb://auth-service"))

			.route("customer-service-swagger", r -> r
				.path("/customer-service/**")
				.filters(f -> f
					.rewritePath("/customer-service/(?<segment>.*)", "/${segment}")
					.addRequestHeader("X-Forwarded-Prefix", "/customer-service")
					.addRequestHeader("X-Service-Name", "customer-service"))
				.uri("lb://customer-service"))

			.route("admin-service-swagger", r -> r
				.path("/admin-service/**")
				.filters(f -> f
					.rewritePath("/admin-service/(?<segment>.*)", "/${segment}")
					.addRequestHeader("X-Forwarded-Prefix", "/admin-service")
					.addRequestHeader("X-Service-Name", "admin-service"))
				.uri("lb://admin-service"))

			.route("manager-service-swagger", r -> r
				.path("/manager-service/**")
				.filters(f -> f
					.rewritePath("/manager-service/(?<segment>.*)", "/${segment}")
					.addRequestHeader("X-Forwarded-Prefix", "/manager-service")
					.addRequestHeader("X-Service-Name", "manager-service"))
				.uri("lb://manager-service"))

			.route("store-service-swagger", r -> r
				.path("/store-service/**")
				.filters(f -> f
					.rewritePath("/store-service/(?<segment>.*)", "/${segment}")
					.addRequestHeader("X-Forwarded-Prefix", "/store-service")
					.addRequestHeader("X-Service-Name", "store-service"))
				.uri("lb://store-service"))

			.route("order-service-swagger", r -> r
				.path("/order-service/**")
				.filters(f -> f
					.rewritePath("/order-service/(?<segment>.*)", "/${segment}")
					.addRequestHeader("X-Forwarded-Prefix", "/order-service")
					.addRequestHeader("X-Service-Name", "order-service"))
				.uri("lb://order-service"))

			.route("payment-service-swagger", r -> r
				.path("/payment-service/**")
				.filters(f -> f
					.rewritePath("/payment-service/(?<segment>.*)", "/${segment}")
					.addRequestHeader("X-Forwarded-Prefix", "/payment-service")
					.addRequestHeader("X-Service-Name", "payment-service"))
				.uri("lb://payment-service"))

			.build();
	}
}