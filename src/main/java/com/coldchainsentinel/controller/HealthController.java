package com.coldchainsentinel.controller;

import com.coldchainsentinel.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing health verification for ColdChain Sentinel backend.
 * This endpoint is independent of database connectivity.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private static final String APP_NAME = "ColdChain Sentinel";
    private static final String STATUS_UP = "UP";
    private static final String STATUS_MSG = "ColdChain Sentinel backend is running";

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> getHealth() {
        HealthResponse response = new HealthResponse(APP_NAME, STATUS_UP, STATUS_MSG);
        return ResponseEntity.ok(response);
    }
}
