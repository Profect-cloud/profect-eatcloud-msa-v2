package com.eatcloud.storeservice.internal.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/internal")
public class StoreInternalProbeController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("service","store-service","ok",true);
    }
}
