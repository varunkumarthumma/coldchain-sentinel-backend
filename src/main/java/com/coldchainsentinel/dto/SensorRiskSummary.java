package com.coldchainsentinel.dto;

import com.coldchainsentinel.model.RiskTier;

import java.math.BigDecimal;

/**
 * Condensed sensor-level risk summary for facility dashboard aggregation.
 */
public record SensorRiskSummary(
        Long sensorId,
        String sensorCode,
        String sensorName,
        boolean sufficientData,
        BigDecimal riskScore,
        RiskTier riskTier
) {
}
