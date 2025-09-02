package com.eatcloud.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClient tossWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.tosspayments.com/v1")
                .build();
    }
} 