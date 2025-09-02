package com.eatcloud.adminservice.external.store;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class StoreInternalPingClient {

    @Qualifier("storeRestClient")
    private final RestClient storeRestClient;

    public Map<String, Object> ping() {
        return storeRestClient.get()
                .uri("/internal/ping")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
