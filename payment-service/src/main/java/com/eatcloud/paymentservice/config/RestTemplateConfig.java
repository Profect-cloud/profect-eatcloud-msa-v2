package com.eatcloud.paymentservice.config;

import com.eatcloud.logging.interceptor.RestTemplateLoggingInterceptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class RestTemplateConfig {

    private final RestTemplateLoggingInterceptor loggingInterceptor;

    public RestTemplateConfig(RestTemplateLoggingInterceptor loggingInterceptor) {
        this.loggingInterceptor = loggingInterceptor;
    }

    @Bean
    @Profile("!aws")
    @LoadBalanced
    public RestTemplate LocalRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder.build();
        
        // ⭐ MDC 전파를 위한 Interceptor 추가
        restTemplate.setInterceptors(List.of(loggingInterceptor));
        
        return restTemplate;
    }

    @Bean
    @Profile("aws")
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder.build();
        
        // ⭐ MDC 전파를 위한 Interceptor 추가
        restTemplate.setInterceptors(List.of(loggingInterceptor));
        
        return restTemplate;
    }
}
