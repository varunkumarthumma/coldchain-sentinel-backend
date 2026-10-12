package com.coldchainsentinel.dto;

import com.coldchainsentinel.model.SensorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for registering a new IoT Sensor.
 */
public record CreateSensorRequest(

        @NotBlank(message = "Sensor code is required")
        @Size(max = 80, message = "Sensor code must not exceed 80 characters")
        String sensorCode,

        @NotBlank(message = "Sensor name is required")
        @Size(max = 120, message = "Sensor name must not exceed 120 characters")
        String name,

        @NotNull(message = "Sensor type is required")
        SensorType sensorType,

        @NotNull(message = "Facility ID is required")
        Long facilityId
) {
}
