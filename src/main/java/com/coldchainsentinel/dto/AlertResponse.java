package com.coldchainsentinel.dto;

import com.coldchainsentinel.model.Alert;
import com.coldchainsentinel.model.AlertSeverity;
import com.coldchainsentinel.model.AlertStatus;
import com.coldchainsentinel.model.AlertType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response payload representing an environmental alert.
 */
public record AlertResponse(
        Long id,
        Long sensorId,
        String sensorCode,
        Long facilityId,
        String facilityName,
        AlertType alertType,
        AlertSeverity severity,
        AlertStatus status,
        String message,
        BigDecimal measuredValue,
        BigDecimal thresholdValue,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime acknowledgedAt,
        LocalDateTime resolvedAt
) {
    public static AlertResponse fromEntity(Alert alert) {
        Long sensorId = alert.getSensor() != null ? alert.getSensor().getId() : null;
        String sensorCode = alert.getSensor() != null ? alert.getSensor().getSensorCode() : null;
        Long facilityId = alert.getFacility() != null ? alert.getFacility().getId() : null;
        String facilityName = alert.getFacility() != null ? alert.getFacility().getName() : null;

        return new AlertResponse(
                alert.getId(),
                sensorId,
                sensorCode,
                facilityId,
                facilityName,
                alert.getAlertType(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getMessage(),
                alert.getMeasuredValue(),
                alert.getThresholdValue(),
                alert.getCreatedAt(),
                alert.getUpdatedAt(),
                alert.getAcknowledgedAt(),
                alert.getResolvedAt()
        );
    }
}
