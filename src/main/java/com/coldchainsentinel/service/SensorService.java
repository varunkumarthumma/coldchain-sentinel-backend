package com.coldchainsentinel.service;

import com.coldchainsentinel.dto.CreateSensorRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.SensorResponse;
import com.coldchainsentinel.dto.UpdateSensorRequest;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.Sensor;
import com.coldchainsentinel.model.StorageFacility;
import com.coldchainsentinel.repository.SensorRepository;
import com.coldchainsentinel.repository.StorageFacilityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business service managing IoT telemetry sensors.
 */
@Service
@Transactional
public class SensorService {

    private final SensorRepository sensorRepository;
    private final StorageFacilityRepository facilityRepository;
    private final com.coldchainsentinel.config.DeviceSecurityProperties securityProperties;

    @org.springframework.beans.factory.annotation.Autowired
    public SensorService(SensorRepository sensorRepository,
                         StorageFacilityRepository facilityRepository,
                         com.coldchainsentinel.config.DeviceSecurityProperties securityProperties) {
        this.sensorRepository = sensorRepository;
        this.facilityRepository = facilityRepository;
        this.securityProperties = securityProperties;
    }

    public SensorService(SensorRepository sensorRepository, StorageFacilityRepository facilityRepository) {
        this(sensorRepository, facilityRepository, new com.coldchainsentinel.config.DeviceSecurityProperties(false, false));
    }

    /**
     * Register a new sensor associated with an existing storage facility.
     */
    public SensorResponse registerSensor(CreateSensorRequest request) {
        StorageFacility facility = facilityRepository.findById(request.facilityId())
                .orElseThrow(() -> new ResourceNotFoundException("Storage facility not found with id: " + request.facilityId()));

        if (sensorRepository.existsBySensorCode(request.sensorCode())) {
            throw new IllegalArgumentException("Sensor code already exists: " + request.sensorCode());
        }

        Sensor sensor = new Sensor(
                request.sensorCode(),
                request.name(),
                request.sensorType(),
                facility
        );

        Sensor saved = sensorRepository.save(sensor);
        return SensorResponse.fromEntity(saved);
    }

    /**
     * List all registered sensors with pagination support.
     */
    @Transactional(readOnly = true)
    public PagedResponse<SensorResponse> getAllSensors(Pageable pageable) {
        Page<Sensor> page = sensorRepository.findAll(pageable);
        Page<SensorResponse> responsePage = page.map(SensorResponse::fromEntity);
        return PagedResponse.fromPage(responsePage);
    }

    /**
     * Retrieve a specific sensor by its ID.
     */
    @Transactional(readOnly = true)
    public SensorResponse getSensorById(Long id) {
        Sensor sensor = findSensorOrThrow(id);
        return SensorResponse.fromEntity(sensor);
    }

    /**
     * List all sensors installed in a specific facility.
     */
    @Transactional(readOnly = true)
    public List<SensorResponse> getSensorsByFacility(Long facilityId) {
        if (!facilityRepository.existsById(facilityId)) {
            throw new ResourceNotFoundException("Storage facility not found with id: " + facilityId);
        }

        return sensorRepository.findByFacilityId(facilityId)
                .stream()
                .map(SensorResponse::fromEntity)
                .toList();
    }

    /**
     * Update an existing sensor's metadata or facility assignment.
     */
    public SensorResponse updateSensor(Long id, UpdateSensorRequest request) {
        Sensor sensor = findSensorOrThrow(id);

        StorageFacility facility = facilityRepository.findById(request.facilityId())
                .orElseThrow(() -> new ResourceNotFoundException("Storage facility not found with id: " + request.facilityId()));

        sensor.setName(request.name());
        sensor.setSensorType(request.sensorType());
        sensor.setFacility(facility);

        if (request.active() != null) {
            sensor.setActive(request.active());
        }

        Sensor updated = sensorRepository.save(sensor);
        return SensorResponse.fromEntity(updated);
    }

    /**
     * Soft-deactivate a sensor without deleting historical records.
     */
    public SensorResponse deactivateSensor(Long id) {
        Sensor sensor = findSensorOrThrow(id);
        sensor.setActive(false);
        Sensor updated = sensorRepository.save(sensor);
        return SensorResponse.fromEntity(updated);
    }

    /**
     * Securely generates and provisions a cryptographic device token for a sensor.
     * Stores only the SHA-256 hash in the database and delivers the raw token once.
     * In production, open token provisioning is rejected without administrative authorization.
     */
    public com.coldchainsentinel.dto.ProvisionDeviceTokenResponse provisionDeviceToken(Long id) {
        if (!securityProperties.isAllowOpenProvisioning()) {
            throw new com.coldchainsentinel.exception.ProvisioningDisabledException(
                    "Device token provisioning is disabled in production without administrative authorization. " +
                    "Enable 'coldchain.security.allow-open-provisioning' only in local development."
            );
        }

        Sensor sensor = findSensorOrThrow(id);
        String rawToken = com.coldchainsentinel.security.DeviceTokenUtil.generateRawToken();
        String hash = com.coldchainsentinel.security.DeviceTokenUtil.hashToken(rawToken);

        sensor.setTokenHash(hash);
        sensorRepository.save(sensor);

        return com.coldchainsentinel.dto.ProvisionDeviceTokenResponse.of(sensor.getId(), sensor.getSensorCode(), rawToken);
    }

    private Sensor findSensorOrThrow(Long id) {
        return sensorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sensor not found with id: " + id));
    }
}
