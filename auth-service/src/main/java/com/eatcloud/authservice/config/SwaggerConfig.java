package com.eatcloud.authservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        Server gatewayServer = new Server()
                .url("/auth-service")
                .description("Auth Service via API Gateway");
                
        Server localServer = new Server()
                .url("/")
                .description("Direct Auth Service");
        
        return new OpenAPI()
                .servers(List.of(gatewayServer, localServer))
                .info(new Info()
                        .title("Auth Service API")
                        .version("1.0")
                        .description("Auth Service API Documentation\n\n" +
                                "### Authentication Flow:\n" +
                                "1. **Register**: POST `/api/v1/auth/register` or `/api/v1/auth/register-test` (for testing)\n" +
                                "2. **Login**: POST `/api/v1/auth/login` with email & password\n" +
                                "3. **Get Token**: Receive JWT access token and refresh token\n" +
                                "4. **Exchange for Passport**: POST `/api/v1/auth/token/exchange` with Bearer token for internal service passport\n\n" +
                                "### Note:\n" +
                                "Most endpoints in Auth Service are public and don't require authentication"));
    }
}
