package com.eatcloud.storeservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientsConfig {

    @Bean
    @Profile("!aws")
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @Profile("aws")
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean(name = "adminRestClient")
    public RestClient adminRestClient(
        @Value("${admin.service-id:admin-service}") String serviceId,
        RestClient.Builder builder
    ) {
        return builder
            .baseUrl("http://" + serviceId)  // lb:// 제거
            .build();
    }

    @Bean(name = "ordersRestClient")
    public RestClient ordersRestClient(
        @Value("${orders.service-id:orders-service}") String serviceId,
        RestClient.Builder builder
    ) {
        return builder
            .baseUrl("http://" + serviceId)  // lb:// 제거
            .build();
    }
}