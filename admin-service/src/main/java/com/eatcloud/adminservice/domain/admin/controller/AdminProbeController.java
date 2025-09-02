package com.eatcloud.adminservice.domain.admin.controller;

import com.eatcloud.adminservice.external.store.StoreInternalPingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/_probe")
@RequiredArgsConstructor
public class AdminProbeController {

    private final StoreInternalPingClient storePing;

    @GetMapping("/self")
    public Map<String, Object> self() {
        return Map.of("service","admin-service","ok",true);
    }

    @GetMapping("/store-ping")
    public Map<String, Object> storePing() {
        return storePing.ping();
    }
}
