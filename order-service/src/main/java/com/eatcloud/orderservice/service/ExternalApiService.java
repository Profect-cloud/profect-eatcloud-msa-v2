package com.eatcloud.orderservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.UUID;

@Slf4j
@Service
public class ExternalApiService {

    private final RestTemplate restTemplate;

    @Value("${inventory.base-url:http://store-service}")
    private String inventoryBaseUrl;

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

    // 공통 POST 유틸
    private <T> ResponseEntity<T> postJson(String url, Object body, String bearer, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null && !bearer.isBlank()) {
            headers.setBearerAuth(bearer);
        }
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url, HttpMethod.POST, entity, type);
    }

    // DTOs
    public static class ReserveReq {
        private UUID orderId;
        private UUID orderLineId;
        private UUID menuId;
        private int qty;
        public ReserveReq() {}
        public ReserveReq(UUID orderId, UUID orderLineId, UUID menuId, int qty) {
            this.orderId = orderId; this.orderLineId = orderLineId; this.menuId = menuId; this.qty = qty;
        }
        public UUID getOrderId() { return orderId; }
        public UUID getOrderLineId() { return orderLineId; }
        public UUID getMenuId() { return menuId; }
        public int getQty() { return qty; }
        public void setOrderId(UUID orderId) { this.orderId = orderId; }
        public void setOrderLineId(UUID orderLineId) { this.orderLineId = orderLineId; }
        public void setMenuId(UUID menuId) { this.menuId = menuId; }
        public void setQty(int qty) { this.qty = qty; }
    }

    public static class ConfirmReq {
        private UUID orderLineId;
        public ConfirmReq() {}
        public ConfirmReq(UUID orderLineId) { this.orderLineId = orderLineId; }
        public UUID getOrderLineId() { return orderLineId; }
        public void setOrderLineId(UUID orderLineId) { this.orderLineId = orderLineId; }
    }

    public static class CancelAfterConfirmReq {
        private UUID orderLineId;
        private String reason;
        public CancelAfterConfirmReq() {}
        public CancelAfterConfirmReq(UUID orderLineId, String reason) {
            this.orderLineId = orderLineId; this.reason = reason;
        }
        public UUID getOrderLineId() { return orderLineId; }
        public String getReason() { return reason; }
        public void setOrderLineId(UUID orderLineId) { this.orderLineId = orderLineId; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public boolean reserveInventory(UUID orderId, UUID orderLineId, UUID menuId, int qty, String bearerToken) {
        try {
            String url = inventoryBaseUrl + "/api/v1/stores/inventory/reserve";
            var res = postJson(url, new ReserveReq(orderId, orderLineId, menuId, qty), bearerToken, Void.class);
            log.info("inventory.reserve OK lineId={} status={}", orderLineId, res.getStatusCode());
            return res.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("inventory.reserve FAIL lineId={} err={}", orderLineId, e.toString());
            return false;
        }
    }

    public boolean confirmInventory(UUID orderLineId, String bearerToken) {
        try {
            String url = inventoryBaseUrl + "/api/v1/stores/inventory/confirm";
            var res = postJson(url, new ConfirmReq(orderLineId), bearerToken, Void.class);
            log.info("inventory.confirm OK lineId={} status={}", orderLineId, res.getStatusCode());
            return res.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("inventory.confirm FAIL lineId={} err={}", orderLineId, e.toString());
            return false;
        }
    }

    public boolean cancelInventoryAfterConfirm(UUID orderLineId, String reason, String bearerToken) {
        try {
            String url = inventoryBaseUrl + "/api/v1/stores/inventory/cancel-after-confirm";
            var res = postJson(url, new CancelAfterConfirmReq(orderLineId, reason), bearerToken, Void.class);
            log.info("inventory.cancelAfterConfirm OK lineId={} status={}", orderLineId, res.getStatusCode());
            return res.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("inventory.cancelAfterConfirm FAIL lineId={} err={}", orderLineId, e.toString());
            return false;
        }
    }
}
