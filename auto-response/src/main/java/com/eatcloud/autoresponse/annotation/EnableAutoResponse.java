package com.eatcloud.autoresponse.annotation;

import java.lang.annotation.*;
import org.springframework.context.annotation.Import;

import com.eatcloud.autoresponse.config.AutoResponseConfiguration;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(AutoResponseConfiguration.class)
public @interface EnableAutoResponse {}
