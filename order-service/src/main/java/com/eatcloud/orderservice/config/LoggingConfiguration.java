package com.eatcloud.orderservice.config;

import com.eatcloud.logging.config.LoggingConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(LoggingConfig.class)
public class LoggingConfiguration {
}
