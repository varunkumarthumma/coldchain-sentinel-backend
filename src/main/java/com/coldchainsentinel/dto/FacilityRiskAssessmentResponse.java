package com.coldchainsentinel.dto;

import com.coldchainsentinel.model.FacilityType;
import com.coldchainsentinel.model.RiskTier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REST response payload for a facility-wide post-harvest risk assessment.
 */
public record FacilityRiskAssessmentResponse(
        Long facilityId,
        String facilityName,
        FacilityType facilityType,
        String location,
        int windowHours,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        int totalActiveSensors,
        int sensorsWithSufficientData,
        int sensorsWithInsufficientData,
        BigDecimal facilityRiskScore,
        RiskTier facilityRiskTier,
        List<SensorRiskSummary> hotspotSensors,
        List<SensorRiskAssessmentResponse> sensorAssessments,
        List<String> facilityRecommendations,
        String aggregationMethodDescription,
        String disclaimer
) {
}
