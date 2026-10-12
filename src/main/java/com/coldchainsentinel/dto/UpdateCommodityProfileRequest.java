package com.coldchainsentinel.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request payload for updating an existing Commodity Profile.
 */
public record UpdateCommodityProfileRequest(

        @NotBlank(message = "Profile name is required")
        @Size(max = 120, message = "Profile name must not exceed 120 characters")
        String name,

        @NotBlank(message = "Commodity name is required")
        @Size(max = 120, message = "Commodity name must not exceed 120 characters")
        String commodityName,

        @Size(max = 120, message = "Variety must not exceed 120 characters")
        String variety,

        @NotNull(message = "Minimum target temperature is required")
        @DecimalMin(value = "-50.00", message = "Minimum temperature cannot be below -50.00 °C")
        @DecimalMax(value = "60.00", message = "Minimum temperature cannot exceed 60.00 °C")
        BigDecimal targetTemperatureMin,

        @NotNull(message = "Maximum target temperature is required")
        @DecimalMin(value = "-50.00", message = "Maximum temperature cannot be below -50.00 °C")
        @DecimalMax(value = "60.00", message = "Maximum temperature cannot exceed 60.00 °C")
        BigDecimal targetTemperatureMax,

        @NotNull(message = "Minimum target humidity is required")
        @DecimalMin(value = "0.00", message = "Minimum humidity cannot be below 0.00 %")
        @DecimalMax(value = "100.00", message = "Minimum humidity cannot exceed 100.00 %")
        BigDecimal targetHumidityMin,

        @NotNull(message = "Maximum target humidity is required")
        @DecimalMin(value = "0.00", message = "Minimum humidity cannot be below 0.00 %")
        @DecimalMax(value = "100.00", message = "Minimum humidity cannot exceed 100.00 %")
        BigDecimal targetHumidityMax,

        Boolean active
) {
}
