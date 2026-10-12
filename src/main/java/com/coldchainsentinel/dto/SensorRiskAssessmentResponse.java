package com.coldchainsentinel.dto;

import com.coldchainsentinel.model.SensorType;

import java.time.LocalDateTime;

/**
 * REST response payload for a sensor-level post-harvest risk assessment.
 */
public record SensorRiskAssessmentResponse(
        Long sensorId,
        String sensorCode,
        String sensorName,
        SensorType sensorType,
        Long facilityId,
        String facilityName,
        int windowHours,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        RiskAssessmentResult assessment
) {
}
