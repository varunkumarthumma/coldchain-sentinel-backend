package com.coldchainsentinel.dto;

import com.coldchainsentinel.model.FacilityType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Request payload for updating an existing Storage Facility.
 */
public record UpdateStorageFacilityRequest(

        @NotBlank(message = "Facility name is required")
        @Size(max = 120, message = "Facility name must not exceed 120 characters")
        String name,

        @NotBlank(message = "Location is required")
        @Size(max = 255, message = "Location must not exceed 255 characters")
        String location,

        @NotNull(message = "Capacity in tonnes is required")
        @DecimalMin(value = "0.01", message = "Capacity must be greater than zero")
        BigDecimal capacityInTonnes,

        @NotNull(message = "Facility type is required")
        FacilityType facilityType,

        Boolean active,

        Long commodityProfileId
) {
    public UpdateStorageFacilityRequest(String name, String location, BigDecimal capacityInTonnes, FacilityType facilityType, Boolean active) {
        this(name, location, capacityInTonnes, facilityType, active, null);
    }
}


