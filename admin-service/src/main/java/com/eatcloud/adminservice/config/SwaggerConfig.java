package com.eatcloud.adminservice.config;

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

        // Passport Token 인증 설정 (내부 서비스용)
        SecurityScheme passportAuth = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("Passport token from /api/v1/auth/token/exchange endpoint (for internal service communication)");

        Components components = new Components()
                .addSecuritySchemes("bearerAuth", bearerAuth)
                .addSecuritySchemes("passportAuth", passportAuth);

        // 기본적으로 bearerAuth 사용
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("bearerAuth");

        Server gatewayServer = new Server()
                .url("/admin-service")
                .description("Admin Service via API Gateway");
                
        Server localServer = new Server()
                .url("/")
                .description("Direct Admin Service");
        
        return new OpenAPI()
                .servers(List.of(gatewayServer, localServer))
                .components(components)
                .addSecurityItem(securityRequirement)
                .info(new Info()
                        .title("Admin Service API")
                        .version("1.0")
                        .description("Admin Service API Documentation\n\n" +
                                "### Authentication:\n" +
                                "1. **User Token**: Login via `/api/v1/auth/login` to get JWT token\n" +
                                "2. **Passport Token**: Exchange user token via `/api/v1/auth/token/exchange` for internal service communication\n" +
                                "3. Click 'Authorize' button and enter: `Bearer {your-token}`"));
    }
}
