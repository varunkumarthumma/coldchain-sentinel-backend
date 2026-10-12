package com.coldchainsentinel.service;

import com.coldchainsentinel.dto.CreateSensorReadingRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.SensorReadingResponse;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.Sensor;
import com.coldchainsentinel.model.SensorReading;
import com.coldchainsentinel.model.SensorType;
import com.coldchainsentinel.repository.SensorReadingRepository;
import com.coldchainsentinel.repository.SensorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Business service managing telemetry sensor readings and telemetry history.
 */
@Service
@Transactional
public class SensorReadingService {

    public static final int MAX_BATCH_SIZE = 500;

    private final SensorReadingRepository readingRepository;
    private final SensorRepository sensorRepository;
    private final AlertService alertService;
    private final jakarta.validation.Validator validator;
    private final com.coldchainsentinel.config.DeviceSecurityProperties securityProperties;

    @org.springframework.beans.factory.annotation.Autowired
    public SensorReadingService(SensorReadingRepository readingRepository,
                                SensorRepository sensorRepository,
                                AlertService alertService,
                                jakarta.validation.Validator validator,
                                com.coldchainsentinel.config.DeviceSecurityProperties securityProperties) {
        this.readingRepository = readingRepository;
        this.sensorRepository = sensorRepository;
        this.alertService = alertService;
        this.validator = validator;
        this.securityProperties = securityProperties;
    }

    public SensorReadingService(SensorReadingRepository readingRepository,
                                SensorRepository sensorRepository,
                                AlertService alertService,
                                jakarta.validation.Validator validator) {
        this(readingRepository, sensorRepository, alertService, validator, new com.coldchainsentinel.config.DeviceSecurityProperties(false, false));
    }

    /**
     * Ingest and persist a new telemetry reading without explicit token (overload).
     */
    public SensorReadingResponse recordReading(Long sensorId, CreateSensorReadingRequest request) {
        return recordReading(sensorId, request, null);
    }

    /**
     * Ingest and persist a batch of telemetry readings without explicit token (overload).
     */
    public com.coldchainsentinel.dto.BatchSensorReadingResponse recordReadingsBatch(Long sensorId, List<CreateSensorReadingRequest> requests) {
        return recordReadingsBatch(sensorId, requests, null);
    }

    /**
     * Ingest and persist a new telemetry reading for an active sensor.
     * Evaluates the saved reading against environmental thresholds to trigger alerts.
     */
    public SensorReadingResponse recordReading(Long sensorId, CreateSensorReadingRequest request, String providedRawToken) {
        Sensor sensor = sensorRepository.findById(sensorId)
                .orElseThrow(() -> new ResourceNotFoundException("Sensor not found with id: " + sensorId));

        if (!sensor.isActive()) {
            throw new IllegalArgumentException("Sensor is inactive and cannot accept readings. Sensor ID: " + sensorId);
        }

        authenticateDevice(sensor, providedRawToken);
        validateMeasurementsForSensorType(sensor.getSensorType(), request);

        LocalDateTime recordedTime = request.recordedAt() != null ? request.recordedAt() : LocalDateTime.now();

        SensorReading reading = new SensorReading(
                sensor,
                request.temperatureCelsius(),
                request.humidityPercentage(),
                recordedTime,
                request.dataSource()
        );

        SensorReading saved = readingRepository.save(reading);
        alertService.evaluateReading(saved);
        return SensorReadingResponse.fromEntity(saved);
    }

    /**
     * Ingest and persist a batch of telemetry readings transactionally for an active sensor.
     * Validates each item, saves all readings, and triggers alert evaluation for all saved readings.
     */
    public com.coldchainsentinel.dto.BatchSensorReadingResponse recordReadingsBatch(Long sensorId, List<CreateSensorReadingRequest> requests, String providedRawToken) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Batch payload cannot be empty or null");
        }

        if (requests.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(String.format("Batch size of %d exceeds maximum allowed limit of %d", requests.size(), MAX_BATCH_SIZE));
        }

        Sensor sensor = sensorRepository.findById(sensorId)
                .orElseThrow(() -> new ResourceNotFoundException("Sensor not found with id: " + sensorId));

        if (!sensor.isActive()) {
            throw new IllegalArgumentException("Sensor is inactive and cannot accept readings. Sensor ID: " + sensorId);
        }

        authenticateDevice(sensor, providedRawToken);

        // Validate each item in the batch
        for (int i = 0; i < requests.size(); i++) {
            CreateSensorReadingRequest item = requests.get(i);
            if (item == null) {
                throw new IllegalArgumentException(String.format("Batch item at index %d is null", i));
            }

            var violations = validator.validate(item);
            if (!violations.isEmpty()) {
                var first = violations.iterator().next();
                throw new IllegalArgumentException(String.format("Batch item at index %d is invalid: %s %s", i, first.getPropertyPath(), first.getMessage()));
            }

            try {
                validateMeasurementsForSensorType(sensor.getSensorType(), item);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(String.format("Batch item at index %d invalid: %s", i, e.getMessage()));
            }
        }

        List<SensorReading> entities = new java.util.ArrayList<>(requests.size());
        for (CreateSensorReadingRequest item : requests) {
            LocalDateTime recordedTime = item.recordedAt() != null ? item.recordedAt() : LocalDateTime.now();
            entities.add(new SensorReading(
                    sensor,
                    item.temperatureCelsius(),
                    item.humidityPercentage(),
                    recordedTime,
                    item.dataSource()
            ));
        }

        List<SensorReading> savedList = readingRepository.saveAll(entities);

        // Evaluate alerts for each successfully saved reading within this transaction
        for (SensorReading saved : savedList) {
            alertService.evaluateReading(saved);
        }

        List<SensorReadingResponse> responses = savedList.stream()
                .map(SensorReadingResponse::fromEntity)
                .toList();

        return new com.coldchainsentinel.dto.BatchSensorReadingResponse(
                sensor.getId(),
                sensor.getSensorCode(),
                responses.size(),
                responses
        );
    }

    /**
     * Retrieve a specific telemetry reading by its ID.
     */
    @Transactional(readOnly = true)
    public SensorReadingResponse getReadingById(Long readingId) {
        SensorReading reading = readingRepository.findById(readingId)
                .orElseThrow(() -> new ResourceNotFoundException("Sensor reading not found with id: " + readingId));
        return SensorReadingResponse.fromEntity(reading);
    }

    /**
     * Retrieve a paginated list of readings for a sensor, ordered newest-first.
     */
    @Transactional(readOnly = true)
    public PagedResponse<SensorReadingResponse> getReadingsBySensor(Long sensorId, Pageable pageable) {
        if (!sensorRepository.existsById(sensorId)) {
            throw new ResourceNotFoundException("Sensor not found with id: " + sensorId);
        }

        Page<SensorReading> page = readingRepository.findBySensorIdOrderByRecordedAtDesc(sensorId, pageable);
        Page<SensorReadingResponse> responsePage = page.map(SensorReadingResponse::fromEntity);
        return PagedResponse.fromPage(responsePage);
    }

    /**
     * Retrieve readings for a sensor within a specified timestamp range, ordered newest-first.
     */
    @Transactional(readOnly = true)
    public List<SensorReadingResponse> getReadingsBySensorAndRange(Long sensorId, LocalDateTime from, LocalDateTime to) {
        if (!sensorRepository.existsById(sensorId)) {
            throw new ResourceNotFoundException("Sensor not found with id: " + sensorId);
        }

        if (from == null || to == null) {
            throw new IllegalArgumentException("Both 'from' and 'to' timestamp query parameters are required");
        }

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' timestamp must be before or equal to 'to' timestamp");
        }

        return readingRepository.findBySensorIdAndRecordedAtBetweenOrderByRecordedAtDesc(sensorId, from, to)
                .stream()
                .map(SensorReadingResponse::fromEntity)
                .toList();
    }

    /**
     * Validates that the submitted measurements are appropriate for the hardware sensor type.
     */
    private void validateMeasurementsForSensorType(SensorType sensorType, CreateSensorReadingRequest request) {
        if (request.temperatureCelsius() == null && request.humidityPercentage() == null) {
            throw new IllegalArgumentException("Reading must contain at least one measurement (temperature or humidity)");
        }

        switch (sensorType) {
            case TEMPERATURE -> {
                if (request.temperatureCelsius() == null) {
                    throw new IllegalArgumentException("Temperature measurement is required for TEMPERATURE sensor");
                }
                if (request.humidityPercentage() != null) {
                    throw new IllegalArgumentException("Temperature-only sensor cannot accept humidity measurements");
                }
            }
            case HUMIDITY -> {
                if (request.humidityPercentage() == null) {
                    throw new IllegalArgumentException("Humidity measurement is required for HUMIDITY sensor");
                }
                if (request.temperatureCelsius() != null) {
                    throw new IllegalArgumentException("Humidity-only sensor cannot accept temperature measurements");
                }
            }
            case TEMPERATURE_HUMIDITY -> {
                // A TEMPERATURE_HUMIDITY sensor is allowed to submit either or both measurements
            }
        }
    }

    /**
     * Authenticates the ingested device request against the sensor's stored token hash.
     * Enforces constant-time cryptographic verification.
     */
    private void authenticateDevice(Sensor sensor, String providedRawToken) {
        String storedHash = sensor.getTokenHash();
        if (storedHash == null || storedHash.isBlank()) {
            if (!securityProperties.isAllowUnprovisionedSensors()) {
                throw new com.coldchainsentinel.exception.UnauthorizedDeviceException(
                        String.format("Unprovisioned sensor '%s' cannot submit telemetry. Unauthenticated ingestion is disabled in production.", sensor.getSensorCode())
                );
            }
            // In local development mode with allowUnprovisionedSensors=true, allow unauthenticated ingestion
            return;
        }

        if (providedRawToken == null || providedRawToken.isBlank()) {
            throw new com.coldchainsentinel.exception.UnauthorizedDeviceException(
                    String.format("Missing required 'X-Sensor-Token' header for provisioned sensor: %s", sensor.getSensorCode())
            );
        }

        boolean valid = com.coldchainsentinel.security.DeviceTokenUtil.verifyToken(providedRawToken, storedHash);
        if (!valid) {
            throw new com.coldchainsentinel.exception.UnauthorizedDeviceException(
                    String.format("Invalid device token for sensor: %s", sensor.getSensorCode())
            );
        }
    }
}
