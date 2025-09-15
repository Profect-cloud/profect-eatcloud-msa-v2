package com.eatcloud.customerservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    @Profile("!aws")
    @LoadBalanced
    public RestTemplate LocalRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
    @Bean
    @Profile("aws")
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
