package com.eatcloud.storeservice.domain.store.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import com.eatcloud.logging.annotation.Loggable;

@RestController
@RequestMapping("/stores/internal")
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public class StoreGatewayProbeController {
    @GetMapping("/ping")
    public Map<String, Object> gwPing() {
        return Map.of("service","store-service","ok",true);
    }
}
