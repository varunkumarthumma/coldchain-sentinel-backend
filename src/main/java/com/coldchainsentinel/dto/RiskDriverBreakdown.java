package com.coldchainsentinel.dto;

import java.math.BigDecimal;

/**
 * Detailed telemetry breakdown and contributing factors for risk scoring.
 */
public record RiskDriverBreakdown(
        BigDecimal thermalExcursionScore,
        BigDecimal humidityExcursionScore,
        BigDecimal durationScore,
        BigDecimal alertPenaltyScore,
        BigDecimal accumulatedDegreeHours,
        long totalExcursionMinutes,
        long maxContinuousExcursionMinutes,
        BigDecimal maxObservedTemperature,
        BigDecimal minObservedTemperature,
        BigDecimal maxObservedHumidity,
        BigDecimal minObservedHumidity,
        int evaluatedAlertsCount,
        int suppressedDuplicateIncidentsCount
) {
}
