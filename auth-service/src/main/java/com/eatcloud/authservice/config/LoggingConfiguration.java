package com.eatcloud.authservice.config;

import com.eatcloud.logging.config.LoggingConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(LoggingConfig.class)
public class LoggingConfiguration {
    // ObjectMapper Bean 완전 제거
}
