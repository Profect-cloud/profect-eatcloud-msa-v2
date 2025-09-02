package com.eatcloud.authservice.controller;

import com.eatcloud.authservice.jwt.JwtTokenProvider;
import com.eatcloud.authservice.jwt.PassportTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "2. PassportController", description = "Passport Token Exchange API")
public class PassportController {

    private final JwtTokenProvider jwtTokenProvider;
    private final PassportTokenService passportTokenService;

    public PassportController(JwtTokenProvider jwtTokenProvider, PassportTokenService passportTokenService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.passportTokenService = passportTokenService;
    }

    @PostMapping("/token/exchange")
    @Operation(
        summary = "Exchange JWT for Passport Token",
        description = "JWT 토큰을 Passport 토큰으로 교환합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully exchanged token"),
        @ApiResponse(responseCode = "400", description = "Missing bearer token"),
        @ApiResponse(responseCode = "401", description = "Invalid token")
    })
    public ResponseEntity<Map<String, Object>> exchange(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        
        System.out.println("===== Token Exchange Debug =====");
        System.out.println("Authorization header received: " + authorization);
        
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            System.out.println("Error: Missing or invalid Bearer token format");
            return ResponseEntity.badRequest().body(Map.of("error", "missing bearer token"));
        }
        
        String token = authorization.substring(7).trim();
        System.out.println("Token extracted: " + token.substring(0, Math.min(20, token.length())) + "...");

        if (!jwtTokenProvider.validateToken(token)) {
            System.out.println("Error: Token validation failed");
            return ResponseEntity.status(401).body(Map.of("error", "invalid token"));
        }

        UUID userId = jwtTokenProvider.getIdFromToken(token);
        String role = jwtTokenProvider.getTypeFromToken(token);
        System.out.println("Token valid - userId: " + userId + ", role: " + role);

        String passport = passportTokenService.issuePassport(
            userId.toString(),
            List.of(role.toUpperCase()),
            600
        );

        System.out.println("Passport issued successfully");
        return ResponseEntity.ok(Map.of(
            "access_token", passport,
            "token_type", "Bearer",
            "expires_in", 600
        ));
    }

    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Get JWKS", description = "Passport 토큰 검증을 위한 공개키 정보")
    public Map<String, Object> jwks() {
        return passportTokenService.getJwks();
    }
}
