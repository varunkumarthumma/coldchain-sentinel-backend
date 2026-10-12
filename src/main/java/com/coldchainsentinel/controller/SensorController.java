package com.coldchainsentinel.controller;

import com.coldchainsentinel.dto.CreateSensorRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.SensorResponse;
import com.coldchainsentinel.dto.UpdateSensorRequest;
import com.coldchainsentinel.service.SensorService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for IoT Telemetry Sensor management.
 */
@RestController
@RequestMapping("/api")
public class SensorController {

    private final SensorService sensorService;

    public SensorController(SensorService sensorService) {
        this.sensorService = sensorService;
    }

    /**
     * Register a new sensor.
     */
    @PostMapping("/sensors")
    public ResponseEntity<SensorResponse> registerSensor(
            @Valid @RequestBody CreateSensorRequest request
    ) {
        SensorResponse response = sensorService.registerSensor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all sensors with pagination.
     */
    @GetMapping("/sensors")
    public ResponseEntity<PagedResponse<SensorResponse>> getAllSensors(
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PagedResponse<SensorResponse> response = sensorService.getAllSensors(pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve a specific sensor by ID.
     */
    @GetMapping("/sensors/{id}")
    public ResponseEntity<SensorResponse> getSensorById(@PathVariable Long id) {
        SensorResponse response = sensorService.getSensorById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * List all sensors installed in a specific facility.
     */
    @GetMapping("/facilities/{facilityId}/sensors")
    public ResponseEntity<List<SensorResponse>> getSensorsByFacility(@PathVariable Long facilityId) {
        List<SensorResponse> response = sensorService.getSensorsByFacility(facilityId);
        return ResponseEntity.ok(response);
    }

    /**
     * Update an existing sensor.
     */
    @PutMapping("/sensors/{id}")
    public ResponseEntity<SensorResponse> updateSensor(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSensorRequest request
    ) {
        SensorResponse response = sensorService.updateSensor(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Soft-deactivate a sensor.
     */
    @PatchMapping("/sensors/{id}/deactivate")
    public ResponseEntity<SensorResponse> deactivateSensor(@PathVariable Long id) {
        SensorResponse response = sensorService.deactivateSensor(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Provision or rotate a cryptographic device token for a sensor.
     * Returns the raw token once. Only its SHA-256 hash is persisted.
     */
    @PostMapping("/sensors/{id}/token")
    public ResponseEntity<com.coldchainsentinel.dto.ProvisionDeviceTokenResponse> provisionToken(@PathVariable Long id) {
        com.coldchainsentinel.dto.ProvisionDeviceTokenResponse response = sensorService.provisionDeviceToken(id);
        return ResponseEntity.ok(response);
    }
}
