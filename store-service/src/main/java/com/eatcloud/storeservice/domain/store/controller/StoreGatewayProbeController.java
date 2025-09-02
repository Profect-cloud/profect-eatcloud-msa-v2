package com.eatcloud.storeservice.domain.store.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/stores/internal")
public class StoreGatewayProbeController {
    @GetMapping("/ping")
    public Map<String, Object> gwPing() {
        return Map.of("service","store-service","ok",true);
    }
}
