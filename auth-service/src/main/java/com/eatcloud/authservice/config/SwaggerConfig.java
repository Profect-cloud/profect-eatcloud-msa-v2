package com.eatcloud.authservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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

        Server gatewayServer = new Server()
                .url("/auth-service")
                .description("Auth Service via API Gateway");
                
        Server localServer = new Server()
                .url("/")
                .description("Direct Auth Service");
        
        return new OpenAPI()
                .servers(List.of(gatewayServer, localServer))
                .components(components)
                .info(new Info()
                        .title("Auth Service API")
                        .version("1.0")
                        .description("Auth Service API Documentation\n\n" +
                                "### Authentication Flow:\n" +
                                "1. **Register**: POST `/api/v1/auth/register` or `/api/v1/auth/register-test` (for testing)\n" +
                                "2. **Login**: POST `/api/v1/auth/login` with email & password\n" +
                                "3. **Get Token**: Receive JWT access token and refresh token\n" +
                                "4. **Exchange for Passport**: POST `/api/v1/auth/token/exchange` with Bearer token for internal service passport\n\n" +
                                "### Token Exchange 사용법:\n" +
                                "1. 먼저 로그인하여 JWT 토큰 획득\n" +
                                "2. Swagger의 'Authorize' 버튼 클릭\n" +
                                "3. 입력창에 `Bearer {token}` 형식으로 입력 (Bearer 다음 공백 주의!)\n" +
                                "4. `/token/exchange` 엔드포인트 호출\n" +
                                "5. 반환된 passport 토큰을 다른 서비스에서 사용\n\n" +
                                "### Note:\n" +
                                "- Most endpoints in Auth Service are public\n" +
                                "- Only `/token/exchange` requires JWT authentication"));
    }
}
