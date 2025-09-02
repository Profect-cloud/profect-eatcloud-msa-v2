package com.eatcloud.authservice.service;

import com.eatcloud.authservice.dto.UserDto;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class RefreshTokenService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;

    private static final String BLACKLIST_PREFIX = "blacklist:";

    public RefreshTokenService(RedisTemplate<String, Object> redisTemplate, RestTemplate restTemplate) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
    }

    private String createKey(Object user) {
        String role;
        UUID id;

        if (user instanceof UserDto dto) {
            role = dto.getRole();
            id = dto.getId();
        } else {
            throw new IllegalArgumentException("Unknown user type");
        }

        return "refresh:" + role + ":" + id;
    }

    public Object findUserByRoleAndId(String role, UUID id) {
        String url = switch (role) {
            case "admin" -> "http://admin-service/api/v1/admin/" + id;
            case "manager" -> "http://manager-service/api/v1/manager/" + id;
            case "customer" -> "http://customer-service/api/v1/customers/" + id;
            default -> throw new IllegalArgumentException("알 수 없는 역할: " + role);
        };
        return restTemplate.getForObject(url, Object.class);
    }

    public void saveOrUpdateToken(Object user, String refreshToken, LocalDateTime expiryDateTime) {
        String key = createKey(user);
        long duration = Duration.between(LocalDateTime.now(), expiryDateTime).getSeconds();
        redisTemplate.opsForValue().set(key, refreshToken, duration, TimeUnit.SECONDS);
    }

    public boolean isValid(Object user, String token) {
        String key = createKey(user);
        Object stored = redisTemplate.opsForValue().get(key);

        if (redisTemplate.hasKey(BLACKLIST_PREFIX + token)) {
            return false;
        }

        return stored != null && stored.equals(token);
    }

    public void addToBlacklist(String token, long expirationSeconds) {
        redisTemplate.opsForValue().set(BLACKLIST_PREFIX + token, "blacklisted", expirationSeconds, TimeUnit.SECONDS);
    }

    public void delete(Object user) {
        String key = createKey(user);
        redisTemplate.delete(key);
    }
}
