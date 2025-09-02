package com.eatcloud.autoresponse.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.eatcloud.autoresponse.ExceptionHandler;

@Configuration
@ComponentScan(basePackageClasses = ExceptionHandler.class)
public class AutoResponseConfiguration { }
