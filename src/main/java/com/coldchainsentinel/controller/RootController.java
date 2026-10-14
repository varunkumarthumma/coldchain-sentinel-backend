package com.coldchainsentinel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Root endpoint controller providing service status and API catalog
 * when opening the Render service URL directly in a browser.
 */
@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
                "application", "ColdChain Sentinel Backend API",
                "status", "UP",
                "message", "ColdChain Sentinel backend is running and connected to PostgreSQL",
                "health", "/api/health",
                "endpoints", Map.of(
                        "facilities", "/api/facilities",
                        "sensors", "/api/sensors",
                        "alerts", "/api/alerts",
                        "commodityProfiles", "/api/commodity-profiles"
                )
        ));
    }
}
