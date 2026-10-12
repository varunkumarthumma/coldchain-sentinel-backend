package com.coldchainsentinel.dto;

import com.coldchainsentinel.model.FacilityType;
import com.coldchainsentinel.model.StorageFacility;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response payload representing a Storage Facility.
 */
public record StorageFacilityResponse(
        Long id,
        String name,
        String location,
        BigDecimal capacityInTonnes,
        FacilityType facilityType,
        boolean active,
        Long commodityProfileId,
        String commodityProfileName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public StorageFacilityResponse(
            Long id,
            String name,
            String location,
            BigDecimal capacityInTonnes,
            FacilityType facilityType,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(id, name, location, capacityInTonnes, facilityType, active, null, null, createdAt, updatedAt);
    }

    public static StorageFacilityResponse fromEntity(StorageFacility entity) {
        Long profileId = entity.getCommodityProfile() != null ? entity.getCommodityProfile().getId() : null;
        String profileName = entity.getCommodityProfile() != null ? entity.getCommodityProfile().getName() : null;

        return new StorageFacilityResponse(
                entity.getId(),
                entity.getName(),
                entity.getLocation(),
                entity.getCapacityInTonnes(),
                entity.getFacilityType(),
                entity.isActive(),
                profileId,
                profileName,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

