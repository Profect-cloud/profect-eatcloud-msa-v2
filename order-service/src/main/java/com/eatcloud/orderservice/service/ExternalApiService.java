package com.eatcloud.orderservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.UUID;

@Slf4j
@Service
public class ExternalApiService {

    private final RestTemplate restTemplate;

    public ExternalApiService(@Qualifier("restTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Boolean checkCustomerExists(UUID customerId) {
        try {
            String url = "http://customer-service/customers/" + customerId + "/exists";

            ResponseEntity<Boolean> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    Boolean.class
            );

            log.info("Customer exists check successful for customerId: {}, result: {}",
                    customerId, response.getBody());
            return response.getBody() != null ? response.getBody() : false;

        } catch (RestClientException e) {
            log.error("Failed to check customer exists for customerId: {}", customerId, e);
            return true;
        }
    }

    public Integer getMenuPrice(UUID menuId) {
        try {
            String url = "http://store-service/stores/menus/" + menuId + "/price";

            ResponseEntity<Integer> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    Integer.class
            );

            Integer price = response.getBody();
            log.info("Menu price retrieved successfully for menuId: {}, price: {}", menuId, price);

            if (price == null) {
                throw new RuntimeException("Menu price is null for menuId: " + menuId);
            }

            return price;

        } catch (RestClientException e) {
            log.error("Failed to get menu price for menuId: {}", menuId, e);
            throw new RuntimeException("Store service is temporarily unavailable for menu: " + menuId, e);
        }
    }

    public Integer getCustomerPoints(UUID customerId, String bearerToken) {
        try {
            String url = "http://customer-service/api/v1/customers/" + customerId + "/points";

            // Authorization 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            if (bearerToken != null && !bearerToken.trim().isEmpty()) {
                headers.setBearerAuth(bearerToken);
            }
            
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Integer> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Integer.class
            );

            Integer points = response.getBody();
            log.info("Customer points retrieved successfully for customerId: {}, points: {}", customerId, points);

            if (points == null) {
                throw new RuntimeException("Customer points is null for customerId: " + customerId);
            }

            return points;

        } catch (RestClientException e) {
            log.error("Failed to get customer points for customerId: {}", customerId, e);
            throw new RuntimeException("Customer service is temporarily unavailable for customer: " + customerId, e);
        }
    }
}
