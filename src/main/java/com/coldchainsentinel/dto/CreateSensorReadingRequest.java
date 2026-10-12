package com.coldchainsentinel.dto;

import com.coldchainsentinel.model.ReadingDataSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request payload for recording a telemetry sensor reading.
 */
public record CreateSensorReadingRequest(

        @DecimalMin(value = "-100.00", message = "Temperature cannot be below -100.00 °C")
        @DecimalMax(value = "100.00", message = "Temperature cannot exceed 100.00 °C")
        BigDecimal temperatureCelsius,

        @DecimalMin(value = "0.00", message = "Humidity percentage cannot be below 0.00%")
        @DecimalMax(value = "100.00", message = "Humidity percentage cannot exceed 100.00%")
        BigDecimal humidityPercentage,

        LocalDateTime recordedAt,

        @NotNull(message = "Data source is required (SIMULATED or DEVICE)")
        ReadingDataSource dataSource
) {
}
