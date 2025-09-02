package com.eatcloud.customerservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        // JWT Bearer Token 인증 설정
        SecurityScheme bearerAuth = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("JWT token from /api/v1/auth/login endpoint");

        Components components = new Components()
                .addSecuritySchemes("bearerAuth", bearerAuth);

        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("bearerAuth");

        Server gatewayServer = new Server()
                .url("/customer-service")
                .description("Customer Service via API Gateway");
                
        Server localServer = new Server()
                .url("/")
                .description("Direct Customer Service");
        
        return new OpenAPI()
                .servers(List.of(gatewayServer, localServer))
                .components(components)
                .addSecurityItem(securityRequirement)
                .info(new Info()
                        .title("Customer Service API")
                        .version("1.0")
                        .description("Customer Service API Documentation\n\n" +
                                "### Authentication:\n" +
                                "1. Login via `/api/v1/auth/login` to get JWT token\n" +
                                "2. Click 'Authorize' button and enter: `Bearer {your-token}`"));
    }
}
