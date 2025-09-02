package com.eatcloud.authservice.controller;

import com.eatcloud.authservice.jwt.JwtTokenProvider;
import com.eatcloud.authservice.jwt.PassportTokenService;
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
public class PassportController {

    private final JwtTokenProvider jwtTokenProvider;
    private final PassportTokenService passportTokenService;

    public PassportController(JwtTokenProvider jwtTokenProvider, PassportTokenService passportTokenService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.passportTokenService = passportTokenService;
    }

    @PostMapping("/token/exchange")
    public ResponseEntity<Map<String, Object>> exchange(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing bearer token"));
        }
        String token = authorization.substring(7).trim();

        if (!jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid token"));
        }

        UUID userId = jwtTokenProvider.getIdFromToken(token);
        String role = jwtTokenProvider.getTypeFromToken(token);

        String passport = passportTokenService.issuePassport(
            userId.toString(),
            List.of(role.toUpperCase()),
            600
        );

        return ResponseEntity.ok(Map.of(
            "access_token", passport,
            "token_type", "Bearer",
            "expires_in", 600
        ));
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return passportTokenService.getJwks();
    }
}


