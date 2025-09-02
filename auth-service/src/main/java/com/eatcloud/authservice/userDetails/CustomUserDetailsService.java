package com.eatcloud.authservice.userDetails;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.client.RestTemplate;

@Service
public class CustomUserDetailsService implements UserDetailsService {

	private final RestTemplate restTemplate;
	private final String gatewayUrl = "http://api-gateway";

	public CustomUserDetailsService(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	public UserDetails loadUserByIdAndType(UUID id, String type) {
		Map<?, ?> user = getUserFromService(type, id);

		if (user == null) {
			throw new UsernameNotFoundException(type + "을 찾을 수 없습니다: " + id);
		}

		String password = user.get("password").toString();
		String role = switch (type.toLowerCase()) {
			case "admin" -> "ADMIN";
			case "manager" -> "MANAGER";
			case "customer" -> "CUSTOMER";
			default -> throw new IllegalArgumentException("알 수 없는 사용자 타입: " + type);
		};

		return User.builder()
				.username(id.toString())
				.password(password)
				.roles(role)
				.build();
	}

	private Map<?, ?> getUserFromService(String type, UUID id) {
		String serviceName = switch (type.toLowerCase()) {
			case "admin", "manager" -> type + "-service";
			case "customer" -> "customer-service";
			default -> throw new IllegalArgumentException("알 수 없는 사용자 타입: " + type);
		};

		String path = switch (type.toLowerCase()) {
			case "admin", "manager" -> type;
			case "customer" -> "customers";
			default -> throw new IllegalArgumentException("알 수 없는 사용자 타입: " + type);
		};

		String url = "http://" + serviceName + "/api/v1/" + path + "/" + id;

		try {
			return restTemplate.getForObject(url, Map.class);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		throw new UnsupportedOperationException("loadUserByIdAndType(UUID id, String type)을 사용하세요.");
	}
}
