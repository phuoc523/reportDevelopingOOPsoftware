package com.scentstation.reporting.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/reporting/health")
    public Map<String, String> health() {
        return Map.of(
                "service", "reporting-service",
                "status", "UP"
        );
    }
}
