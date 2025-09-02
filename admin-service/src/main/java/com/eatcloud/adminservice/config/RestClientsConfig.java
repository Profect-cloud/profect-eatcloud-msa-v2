package com.eatcloud.adminservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientsConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    // Store 내부 호출용 RestClient  (@Bean 이름을 storeInternalClient로!)
    @Bean(name = "storeRestClient")
    public RestClient storeRestClient(
            @Value("${store.base-url:http://store-service}") String baseUrl,
            RestClient.Builder lbBuilder
    ) {
        return lbBuilder.baseUrl(baseUrl).build(); // 유레카로 라우팅
    }
}
