package com.eatcloud.orderservice.config;

import com.eatcloud.logging.interceptor.RestTemplateLoggingInterceptor;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import java.util.List;

@Configuration
public class RestTemplateConfig {

    private final RestTemplateLoggingInterceptor loggingInterceptor;

    public RestTemplateConfig(RestTemplateLoggingInterceptor loggingInterceptor) {
        this.loggingInterceptor = loggingInterceptor;
    }

    @Bean(name = "restTemplate")
    @Profile("!aws")
    @LoadBalanced
    public RestTemplate localRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));

        RestTemplate restTemplate = new RestTemplate(factory);
        
        // ⭐ MDC 전파를 위한 Interceptor 추가
        restTemplate.setInterceptors(List.of(loggingInterceptor));
        
        return restTemplate;
    }

    @Bean(name = "restTemplate")
    @Profile("aws")
    public RestTemplate awsRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));

        RestTemplate restTemplate = new RestTemplate(factory);
        
        // ⭐ MDC 전파를 위한 Interceptor 추가
        restTemplate.setInterceptors(List.of(loggingInterceptor));
        
        return restTemplate;
    }

    @Bean
    public RestTemplate customRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // ⭐ MDC 전파를 위한 Interceptor 추가
        restTemplate.setInterceptors(List.of(loggingInterceptor));
        
        return restTemplate;
    }
}
