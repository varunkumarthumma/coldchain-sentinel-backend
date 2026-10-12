package com.coldchainsentinel.controller;

import com.coldchainsentinel.dto.AlertResponse;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.model.AlertSeverity;
import com.coldchainsentinel.model.AlertStatus;
import com.coldchainsentinel.service.AlertService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing environmental alerts and alert lifecycles.
 */
@RestController
@RequestMapping("/api")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * List all alerts with optional filtering by status and severity, and pagination.
     */
    @GetMapping("/alerts")
    public ResponseEntity<PagedResponse<AlertResponse>> getAlerts(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) AlertSeverity severity,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PagedResponse<AlertResponse> response = alertService.getAlerts(status, severity, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve a specific alert by ID.
     */
    @GetMapping("/alerts/{id}")
    public ResponseEntity<AlertResponse> getAlertById(@PathVariable Long id) {
        AlertResponse response = alertService.getAlertById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * List all alerts for a specific sensor.
     */
    @GetMapping("/sensors/{sensorId}/alerts")
    public ResponseEntity<List<AlertResponse>> getAlertsBySensor(@PathVariable Long sensorId) {
        List<AlertResponse> response = alertService.getAlertsBySensor(sensorId);
        return ResponseEntity.ok(response);
    }

    /**
     * Acknowledge an OPEN alert.
     */
    @PatchMapping("/alerts/{id}/acknowledge")
    public ResponseEntity<AlertResponse> acknowledgeAlert(@PathVariable Long id) {
        AlertResponse response = alertService.acknowledgeAlert(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Resolve an OPEN or ACKNOWLEDGED alert.
     */
    @PatchMapping("/alerts/{id}/resolve")
    public ResponseEntity<AlertResponse> resolveAlert(@PathVariable Long id) {
        AlertResponse response = alertService.resolveAlert(id);
        return ResponseEntity.ok(response);
    }
}
