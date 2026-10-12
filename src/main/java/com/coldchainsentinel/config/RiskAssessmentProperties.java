package com.coldchainsentinel.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Configurable parameters and weighting factors for Post-Harvest Loss Risk Assessment.
 *
 * NOTE: These weights and demonstration thresholds represent an experimental heuristic model.
 * Safe temperature/humidity ranges, respiration decay coefficients, and storage lifespans
 * strictly depend on the specific commodity stored (e.g. leafy greens, pome fruit, dairy).
 */
@Component
@ConfigurationProperties(prefix = "coldchain.risk")
public class RiskAssessmentProperties {

    /**
     * Weight assigned to thermal excursion intensity (degree-hours).
     */
    private BigDecimal weightThermalExcursions = new BigDecimal("0.35");

    /**
     * Weight assigned to relative humidity excursions.
     */
    private BigDecimal weightHumidityExcursions = new BigDecimal("0.25");

    /**
     * Weight assigned to consecutive excursion duration penalties.
     */
    private BigDecimal weightExcursionDuration = new BigDecimal("0.20");

    /**
     * Weight assigned to recent alert counts and severity penalties.
     */
    private BigDecimal weightRecentAlerts = new BigDecimal("0.20");

    /**
     * Minimum number of sensor readings required within the observation window to perform an assessment.
     * Prevents misleading low scores on sparse telemetry.
     */
    private int minReadingsRequired = 3;

    /**
     * Maximum permissible interval (in minutes) between consecutive readings for continuous excursion integration.
     * Telemetry gaps wider than this threshold are treated as disconnected/unmonitored rather than continuous excursions.
     */
    private long maxIntervalMinutes = 120;

    /**
     * Score cutoff for MODERATE risk tier.
     */
    private BigDecimal moderateThreshold = new BigDecimal("25.00");

    /**
     * Score cutoff for ELEVATED risk tier.
     */
    private BigDecimal elevatedThreshold = new BigDecimal("55.00");

    /**
     * Score cutoff for CRITICAL risk tier.
     */
    private BigDecimal criticalThreshold = new BigDecimal("80.00");

    @PostConstruct
    public void validate() {
        BigDecimal totalWeight = weightThermalExcursions
                .add(weightHumidityExcursions)
                .add(weightExcursionDuration)
                .add(weightRecentAlerts);

        if (totalWeight.compareTo(new BigDecimal("0.95")) < 0 || totalWeight.compareTo(new BigDecimal("1.05")) > 0) {
            throw vintageConfigException("Risk assessment weights must sum approximately to 1.0. Current sum: " + totalWeight);
        }

        if (moderateThreshold.compareTo(elevatedThreshold) >= 0 || elevatedThreshold.compareTo(criticalThreshold) >= 0) {
            throw vintageConfigException("Risk tier thresholds must be strictly ascending: moderate < elevated < critical");
        }

        if (minReadingsRequired < 1) {
            throw vintageConfigException("minReadingsRequired must be at least 1");
        }
    }

    private IllegalStateException vintageConfigException(String message) {
        return new IllegalStateException("Invalid RiskAssessmentProperties configuration: " + message);
    }

    public BigDecimal getWeightThermalExcursions() {
        return weightThermalExcursions;
    }

    public void setWeightThermalExcursions(BigDecimal weightThermalExcursions) {
        this.weightThermalExcursions = weightThermalExcursions;
    }

    public BigDecimal getWeightHumidityExcursions() {
        return weightHumidityExcursions;
    }

    public void setWeightHumidityExcursions(BigDecimal weightHumidityExcursions) {
        this.weightHumidityExcursions = weightHumidityExcursions;
    }

    public BigDecimal getWeightExcursionDuration() {
        return weightExcursionDuration;
    }

    public void setWeightExcursionDuration(BigDecimal weightExcursionDuration) {
        this.weightExcursionDuration = weightExcursionDuration;
    }

    public BigDecimal getWeightRecentAlerts() {
        return weightRecentAlerts;
    }

    public void setWeightRecentAlerts(BigDecimal weightRecentAlerts) {
        this.weightRecentAlerts = weightRecentAlerts;
    }

    public int getMinReadingsRequired() {
        return minReadingsRequired;
    }

    public void setMinReadingsRequired(int minReadingsRequired) {
        this.minReadingsRequired = minReadingsRequired;
    }

    public long getMaxIntervalMinutes() {
        return maxIntervalMinutes;
    }

    public void setMaxIntervalMinutes(long maxIntervalMinutes) {
        this.maxIntervalMinutes = maxIntervalMinutes;
    }

    public BigDecimal getModerateThreshold() {
        return moderateThreshold;
    }

    public void setModerateThreshold(BigDecimal moderateThreshold) {
        this.moderateThreshold = moderateThreshold;
    }

    public BigDecimal getElevatedThreshold() {
        return elevatedThreshold;
    }

    public void setElevatedThreshold(BigDecimal elevatedThreshold) {
        this.elevatedThreshold = elevatedThreshold;
    }

    public BigDecimal getCriticalThreshold() {
        return criticalThreshold;
    }

    public void setCriticalThreshold(BigDecimal criticalThreshold) {
        this.criticalThreshold = criticalThreshold;
    }
}
