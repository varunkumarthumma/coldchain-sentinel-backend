package com.coldchainsentinel.service;

import com.coldchainsentinel.config.AlertThresholdProperties;
import com.coldchainsentinel.dto.AlertResponse;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.*;
import com.coldchainsentinel.repository.AlertRepository;
import com.coldchainsentinel.repository.SensorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service managing cold chain environmental alerts, threshold evaluation, and alert lifecycles.
 */
@Service
@Transactional
public class AlertService {

    private final AlertRepository alertRepository;
    private final SensorRepository sensorRepository;
    private final AlertThresholdProperties thresholds;

    public AlertService(AlertRepository alertRepository,
                        SensorRepository sensorRepository,
                        AlertThresholdProperties thresholds) {
        this.alertRepository = alertRepository;
        this.sensorRepository = sensorRepository;
        this.thresholds = thresholds;
    }

    /**
     * Evaluates a persisted sensor reading against configured environmental thresholds.
     * Uses the facility's assigned active commodity profile when present; otherwise falls back to global thresholds.
     * Generates alerts for threshold violations and suppresses duplicate OPEN alerts.
     */
    public List<AlertResponse> evaluateReading(SensorReading reading) {
        List<AlertResponse> generatedAlerts = new ArrayList<>();
        Sensor sensor = reading.getSensor();
        StorageFacility facility = sensor.getFacility();

        // Determine effective limits: use active commodity profile if present on the facility, else global defaults
        CommodityProfile profile = facility != null ? facility.getCommodityProfile() : null;
        boolean hasActiveProfile = profile != null && profile.isActive();

        BigDecimal tempMin = hasActiveProfile ? profile.getTargetTemperatureMin() : thresholds.getTemperatureMin();
        BigDecimal tempMax = hasActiveProfile ? profile.getTargetTemperatureMax() : thresholds.getTemperatureMax();
        BigDecimal humidityMin = hasActiveProfile ? profile.getTargetHumidityMin() : thresholds.getHumidityMin();
        BigDecimal humidityMax = hasActiveProfile ? profile.getTargetHumidityMax() : thresholds.getHumidityMax();

        String profileContext = hasActiveProfile ? " (profile: " + profile.getName() + ")" : "";

        // 1. Evaluate Temperature if measurement is present
        if (reading.getTemperatureCelsius() != null) {
            BigDecimal temp = reading.getTemperatureCelsius();

            // High temperature breach
            if (temp.compareTo(tempMax) > 0) {
                checkAndTriggerAlert(
                        sensor,
                        facility,
                        AlertType.HIGH_TEMPERATURE,
                        temp,
                        tempMax,
                        calculateSeverity(temp.subtract(tempMax), thresholds.getTemperatureCriticalDelta()),
                        String.format("High temperature alert: %.2f °C exceeds configured limit of %.2f °C%s", temp, tempMax, profileContext),
                        generatedAlerts
                );
            }
            // Low temperature breach
            else if (temp.compareTo(tempMin) < 0) {
                checkAndTriggerAlert(
                        sensor,
                        facility,
                        AlertType.LOW_TEMPERATURE,
                        temp,
                        tempMin,
                        calculateSeverity(tempMin.subtract(temp), thresholds.getTemperatureCriticalDelta()),
                        String.format("Low temperature alert: %.2f °C is below configured limit of %.2f °C%s", temp, tempMin, profileContext),
                        generatedAlerts
                );
            }
        }

        // 2. Evaluate Humidity if measurement is present
        if (reading.getHumidityPercentage() != null) {
            BigDecimal humidity = reading.getHumidityPercentage();

            // High humidity breach
            if (humidity.compareTo(humidityMax) > 0) {
                checkAndTriggerAlert(
                        sensor,
                        facility,
                        AlertType.HIGH_HUMIDITY,
                        humidity,
                        humidityMax,
                        calculateSeverity(humidity.subtract(humidityMax), thresholds.getHumidityCriticalDelta()),
                        String.format("High humidity alert: %.2f %% exceeds configured limit of %.2f %%%s", humidity, humidityMax, profileContext),
                        generatedAlerts
                );
            }
            // Low humidity breach
            else if (humidity.compareTo(humidityMin) < 0) {
                checkAndTriggerAlert(
                        sensor,
                        facility,
                        AlertType.LOW_HUMIDITY,
                        humidity,
                        humidityMin,
                        calculateSeverity(humidityMin.subtract(humidity), thresholds.getHumidityCriticalDelta()),
                        String.format("Low humidity alert: %.2f %% is below configured limit of %.2f %%%s", humidity, humidityMin, profileContext),
                        generatedAlerts
                );
            }
        }

        return generatedAlerts;
    }


    /**
     * Checks for existing OPEN alert suppression; triggers and saves a new alert if not suppressed.
     */
    private void checkAndTriggerAlert(Sensor sensor,
                                     StorageFacility facility,
                                     AlertType alertType,
                                     BigDecimal measured,
                                     BigDecimal threshold,
                                     AlertSeverity severity,
                                     String message,
                                     List<AlertResponse> generatedAlerts) {
        boolean openAlertExists = alertRepository.existsBySensorIdAndAlertTypeAndStatus(
                sensor.getId(),
                alertType,
                AlertStatus.OPEN
        );

        if (!openAlertExists) {
            Alert alert = new Alert(
                    sensor,
                    facility,
                    alertType,
                    severity,
                    message,
                    measured,
                    threshold
            );
            Alert saved = alertRepository.save(alert);
            generatedAlerts.add(AlertResponse.fromEntity(saved));
        }
    }

    /**
     * Assigns severity deterministically:
     * - delta >= criticalDelta                     => CRITICAL
     * - delta >= criticalDelta / 2                => HIGH
     * - delta < criticalDelta / 2                 => MEDIUM
     */
    private AlertSeverity calculateSeverity(BigDecimal exceedance, BigDecimal criticalDelta) {
        BigDecimal halfDelta = criticalDelta.divide(new BigDecimal("2.0"), 2, RoundingMode.HALF_UP);
        if (exceedance.compareTo(criticalDelta) >= 0) {
            return AlertSeverity.CRITICAL;
        } else if (exceedance.compareTo(halfDelta) >= 0) {
            return AlertSeverity.HIGH;
        } else {
            return AlertSeverity.MEDIUM;
        }
    }

    /**
     * Retrieve paginated list of alerts with optional status and severity filtering.
     */
    @Transactional(readOnly = true)
    public PagedResponse<AlertResponse> getAlerts(AlertStatus status, AlertSeverity severity, Pageable pageable) {
        Page<Alert> page;
        if (status != null && severity != null) {
            page = alertRepository.findByStatusAndSeverity(status, severity, pageable);
        } else if (status != null) {
            page = alertRepository.findByStatus(status, pageable);
        } else if (severity != null) {
            page = alertRepository.findBySeverity(severity, pageable);
        } else {
            page = alertRepository.findAll(pageable);
        }

        Page<AlertResponse> responsePage = page.map(AlertResponse::fromEntity);
        return PagedResponse.fromPage(responsePage);
    }

    /**
     * Retrieve a specific alert by its ID.
     */
    @Transactional(readOnly = true)
    public AlertResponse getAlertById(Long id) {
        Alert alert = findAlertOrThrow(id);
        return AlertResponse.fromEntity(alert);
    }

    /**
     * Retrieve all alerts associated with a specific sensor.
     */
    @Transactional(readOnly = true)
    public List<AlertResponse> getAlertsBySensor(Long sensorId) {
        if (!sensorRepository.existsById(sensorId)) {
            throw new ResourceNotFoundException("Sensor not found with id: " + sensorId);
        }
        return alertRepository.findBySensorIdOrderByCreatedAtDesc(sensorId)
                .stream()
                .map(AlertResponse::fromEntity)
                .toList();
    }

    /**
     * Transitions an alert from OPEN to ACKNOWLEDGED.
     */
    public AlertResponse acknowledgeAlert(Long id) {
        Alert alert = findAlertOrThrow(id);

        if (alert.getStatus() != AlertStatus.OPEN) {
            throw new IllegalArgumentException(
                    String.format("Cannot acknowledge alert with status: %s. Only OPEN alerts can be acknowledged.", alert.getStatus())
            );
        }

        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedAt(LocalDateTime.now());
        Alert updated = alertRepository.save(alert);
        return AlertResponse.fromEntity(updated);
    }

    /**
     * Transitions an alert from OPEN or ACKNOWLEDGED to RESOLVED.
     */
    public AlertResponse resolveAlert(Long id) {
        Alert alert = findAlertOrThrow(id);

        if (alert.getStatus() == AlertStatus.RESOLVED) {
            throw new IllegalArgumentException("Alert is already resolved.");
        }

        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(LocalDateTime.now());
        Alert updated = alertRepository.save(alert);
        return AlertResponse.fromEntity(updated);
    }

    private Alert findAlertOrThrow(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + id));
    }
}
