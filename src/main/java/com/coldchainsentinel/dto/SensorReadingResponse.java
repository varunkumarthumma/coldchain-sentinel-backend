package com.coldchainsentinel.dto;

import com.coldchainsentinel.model.ReadingDataSource;
import com.coldchainsentinel.model.SensorReading;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response payload representing a recorded sensor reading.
 */
public record SensorReadingResponse(
        Long id,
        Long sensorId,
        String sensorCode,
        BigDecimal temperatureCelsius,
        BigDecimal humidityPercentage,
        LocalDateTime recordedAt,
        LocalDateTime createdAt,
        ReadingDataSource dataSource
) {
    public static SensorReadingResponse fromEntity(SensorReading entity) {
        Long sensorId = entity.getSensor() != null ? entity.getSensor().getId() : null;
        String sensorCode = entity.getSensor() != null ? entity.getSensor().getSensorCode() : null;
        return new SensorReadingResponse(
                entity.getId(),
                sensorId,
                sensorCode,
                entity.getTemperatureCelsius(),
                entity.getHumidityPercentage(),
                entity.getRecordedAt(),
                entity.getCreatedAt(),
                entity.getDataSource()
        );
    }
}
