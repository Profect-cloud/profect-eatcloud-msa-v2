package com.eatcloud.storeservice.config;

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
                .url("/store-service")
                .description("Store Service via API Gateway");
                
        Server localServer = new Server()
                .url("/")
                .description("Direct Store Service");
        
        return new OpenAPI()
                .servers(List.of(gatewayServer, localServer))
                .components(components)
                .addSecurityItem(securityRequirement)
                .info(new Info()
                        .title("Store Service API")
                        .version("1.0")
                        .description("Store Service API Documentation"));
    }
}
