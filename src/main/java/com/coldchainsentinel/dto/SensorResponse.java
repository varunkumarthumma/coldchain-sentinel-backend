package com.coldchainsentinel.dto;

import com.coldchainsentinel.model.Sensor;
import com.coldchainsentinel.model.SensorType;
import java.time.LocalDateTime;

/**
 * Response payload representing an IoT Sensor.
 */
public record SensorResponse(
        Long id,
        String sensorCode,
        String name,
        SensorType sensorType,
        Long facilityId,
        String facilityName,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SensorResponse fromEntity(Sensor entity) {
        Long facilityId = entity.getFacility() != null ? entity.getFacility().getId() : null;
        String facilityName = entity.getFacility() != null ? entity.getFacility().getName() : null;
        return new SensorResponse(
                entity.getId(),
                entity.getSensorCode(),
                entity.getName(),
                entity.getSensorType(),
                facilityId,
                facilityName,
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
