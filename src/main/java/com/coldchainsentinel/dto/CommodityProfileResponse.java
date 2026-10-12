package com.coldchainsentinel.dto;

import com.coldchainsentinel.model.CommodityProfile;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response payload representing a Commodity-Specific Storage Profile.
 */
public record CommodityProfileResponse(
        Long id,
        String name,
        String commodityName,
        String variety,
        BigDecimal targetTemperatureMin,
        BigDecimal targetTemperatureMax,
        BigDecimal targetHumidityMin,
        BigDecimal targetHumidityMax,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CommodityProfileResponse fromEntity(CommodityProfile entity) {
        if (entity == null) {
            return null;
        }
        return new CommodityProfileResponse(
                entity.getId(),
                entity.getName(),
                entity.getCommodityName(),
                entity.getVariety(),
                entity.getTargetTemperatureMin(),
                entity.getTargetTemperatureMax(),
                entity.getTargetHumidityMin(),
                entity.getTargetHumidityMax(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
