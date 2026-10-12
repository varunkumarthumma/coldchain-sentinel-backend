package com.coldchainsentinel.controller;

import com.coldchainsentinel.dto.CreateSensorReadingRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.SensorReadingResponse;
import com.coldchainsentinel.service.SensorReadingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for IoT sensor telemetry readings.
 */
@RestController
@RequestMapping("/api")
public class SensorReadingController {

    private final SensorReadingService readingService;

    public SensorReadingController(SensorReadingService readingService) {
        this.readingService = readingService;
    }

    /**
     * Ingest a new reading for a specific sensor.
     */
    @PostMapping("/sensors/{sensorId}/readings")
    public ResponseEntity<SensorReadingResponse> recordReading(
            @PathVariable Long sensorId,
            @RequestHeader(value = "X-Sensor-Token", required = false) String token,
            @Valid @RequestBody CreateSensorReadingRequest request
    ) {
        SensorReadingResponse response = readingService.recordReading(sensorId, request, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Ingest a batch of readings for a specific sensor transactionally.
     */
    @PostMapping("/sensors/{sensorId}/readings/batch")
    public ResponseEntity<com.coldchainsentinel.dto.BatchSensorReadingResponse> recordReadingsBatch(
            @PathVariable Long sensorId,
            @RequestHeader(value = "X-Sensor-Token", required = false) String token,
            @RequestBody List<CreateSensorReadingRequest> requests
    ) {
        com.coldchainsentinel.dto.BatchSensorReadingResponse response = readingService.recordReadingsBatch(sensorId, requests, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieve a paginated list of readings for a sensor, ordered newest-first.
     */
    @GetMapping("/sensors/{sensorId}/readings")
    public ResponseEntity<PagedResponse<SensorReadingResponse>> getReadingsBySensor(
            @PathVariable Long sensorId,
            @PageableDefault(page = 0, size = 20, sort = "recordedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PagedResponse<SensorReadingResponse> response = readingService.getReadingsBySensor(sensorId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve a single sensor reading by reading ID.
     */
    @GetMapping("/readings/{readingId}")
    public ResponseEntity<SensorReadingResponse> getReadingById(@PathVariable Long readingId) {
        SensorReadingResponse response = readingService.getReadingById(readingId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve sensor readings captured within a specific time range.
     */
    @GetMapping("/sensors/{sensorId}/readings/range")
    public ResponseEntity<List<SensorReadingResponse>> getReadingsBySensorAndRange(
            @PathVariable Long sensorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        List<SensorReadingResponse> response = readingService.getReadingsBySensorAndRange(sensorId, from, to);
        return ResponseEntity.ok(response);
    }
}
