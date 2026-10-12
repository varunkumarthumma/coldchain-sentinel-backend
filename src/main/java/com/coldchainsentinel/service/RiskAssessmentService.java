package com.coldchainsentinel.service;

import com.coldchainsentinel.config.AlertThresholdProperties;
import com.coldchainsentinel.config.RiskAssessmentProperties;
import com.coldchainsentinel.dto.FacilityRiskAssessmentResponse;
import com.coldchainsentinel.dto.RiskAssessmentResult;
import com.coldchainsentinel.dto.RiskDriverBreakdown;
import com.coldchainsentinel.dto.SensorRiskAssessmentResponse;
import com.coldchainsentinel.dto.SensorRiskSummary;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.*;
import com.coldchainsentinel.repository.AlertRepository;
import com.coldchainsentinel.repository.SensorReadingRepository;
import com.coldchainsentinel.repository.SensorRepository;
import com.coldchainsentinel.repository.StorageFacilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service calculating experimental, heuristic Post-Harvest Loss Risk Assessments.
 * Evaluates thermal excursions, degree-hours, humidity deviations, and recent alert history.
 *
 * NOTE: The scores produced are heuristic indicators of physical exposure risk,
 * NOT a trained machine learning model or empirical loss percentage.
 */
@Service
@Transactional(readOnly = true)
public class RiskAssessmentService {

    public static final int MAX_WINDOW_HOURS = 720; // 30 days max observation window

    private final RiskAssessmentProperties riskProperties;
    private final AlertThresholdProperties thresholdProperties;
    private final SensorRepository sensorRepository;
    private final StorageFacilityRepository storageFacilityRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final AlertRepository alertRepository;

    public RiskAssessmentService(RiskAssessmentProperties riskProperties,
                                 AlertThresholdProperties thresholdProperties,
                                 SensorRepository sensorRepository,
                                 StorageFacilityRepository storageFacilityRepository,
                                 SensorReadingRepository sensorReadingRepository,
                                 AlertRepository alertRepository) {
        this.riskProperties = riskProperties;
        this.thresholdProperties = thresholdProperties;
        this.sensorRepository = sensorRepository;
        this.storageFacilityRepository = storageFacilityRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.alertRepository = alertRepository;
    }

    /**
     * Conducts risk assessment for a specific sensor over a requested observation window.
     */
    public SensorRiskAssessmentResponse assessSensorRisk(Long sensorId, int windowHours) {
        validateWindowHours(windowHours);

        Sensor sensor = sensorRepository.findById(sensorId)
                .orElseThrow(() -> new ResourceNotFoundException("Sensor not found with id: " + sensorId));

        LocalDateTime windowEnd = LocalDateTime.now();
        LocalDateTime windowStart = windowEnd.minusHours(windowHours);

        List<SensorReading> readings = sensorReadingRepository
                .findBySensorIdAndRecordedAtBetweenOrderByRecordedAtDesc(sensorId, windowStart, windowEnd);

        List<Alert> alerts = alertRepository
                .findBySensorIdAndCreatedAtBetweenOrderByCreatedAtDesc(sensorId, windowStart, windowEnd);

        RiskAssessmentResult result = assessRisk(readings, alerts, windowStart, windowEnd);

        return new SensorRiskAssessmentResponse(
                sensor.getId(),
                sensor.getSensorCode(),
                sensor.getName(),
                sensor.getSensorType(),
                sensor.getFacility() != null ? sensor.getFacility().getId() : null,
                sensor.getFacility() != null ? sensor.getFacility().getName() : null,
                windowHours,
                windowStart,
                windowEnd,
                result
        );
    }

    /**
     * Conducts facility-wide risk assessment across all active sensors within the facility.
     * Uses a conservative hotspot-weighted aggregation: 60% weight on maximum sensor risk score
     * + 40% weight on average score among sensors with sufficient data. Sensors with insufficient data
     * are explicitly listed rather than assumed to be zero risk.
     */
    public FacilityRiskAssessmentResponse assessFacilityRisk(Long facilityId, int windowHours) {
        validateWindowHours(windowHours);

        StorageFacility facility = storageFacilityRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Storage facility not found with id: " + facilityId));

        if (!facility.isActive()) {
            throw new IllegalArgumentException("Storage facility is inactive and cannot be assessed for operational risk. Facility ID: " + facilityId);
        }

        LocalDateTime windowEnd = LocalDateTime.now();
        LocalDateTime windowStart = windowEnd.minusHours(windowHours);

        List<Sensor> activeSensors = sensorRepository.findByFacilityIdAndActive(facilityId, true);

        List<SensorRiskAssessmentResponse> sensorAssessments = new ArrayList<>();
        List<SensorRiskSummary> summaries = new ArrayList<>();
        List<BigDecimal> validScores = new ArrayList<>();
        List<SensorFacilityContext> contexts = new ArrayList<>();
        Map<Long, SensorFacilityContext> contextMap = new HashMap<>();

        BigDecimal maxScore = BigDecimal.ZERO;
        int sufficientCount = 0;
        int insufficientCount = 0;

        BigDecimal tempMin = thresholdProperties.getTemperatureMin();
        BigDecimal tempMax = thresholdProperties.getTemperatureMax();
        BigDecimal humMin = thresholdProperties.getHumidityMin();
        BigDecimal humMax = thresholdProperties.getHumidityMax();

        for (Sensor sensor : activeSensors) {
            List<SensorReading> readings = sensorReadingRepository
                    .findBySensorIdAndRecordedAtBetweenOrderByRecordedAtDesc(sensor.getId(), windowStart, windowEnd);

            List<Alert> alerts = alertRepository
                    .findBySensorIdAndCreatedAtBetweenOrderByCreatedAtDesc(sensor.getId(), windowStart, windowEnd);

            RiskAssessmentResult result = assessRisk(readings, alerts, windowStart, windowEnd);

            SensorRiskAssessmentResponse response = new SensorRiskAssessmentResponse(
                    sensor.getId(),
                    sensor.getSensorCode(),
                    sensor.getName(),
                    sensor.getSensorType(),
                    facility.getId(),
                    facility.getName(),
                    windowHours,
                    windowStart,
                    windowEnd,
                    result
            );
            sensorAssessments.add(response);

            // Latest reading in the window (readings ordered DESC by recordedAt)
            SensorReading latestReading = (readings != null && !readings.isEmpty()) ? readings.get(0) : null;
            BigDecimal latestTemp = latestReading != null ? latestReading.getTemperatureCelsius() : null;
            BigDecimal latestHum = latestReading != null ? latestReading.getHumidityPercentage() : null;

            boolean isCurrentTempBreach = latestTemp != null &&
                    (latestTemp.compareTo(tempMax) > 0 || latestTemp.compareTo(tempMin) < 0);
            boolean isCurrentHumBreach = latestHum != null &&
                    (latestHum.compareTo(humMax) > 0 || latestHum.compareTo(humMin) < 0);
            boolean isCurrentlyActiveUnsafe = isCurrentTempBreach || isCurrentHumBreach;

            boolean hasThermalExcursion = false;
            boolean hasHumidityExcursion = false;
            boolean hasExcursion = false;

            if (result.sufficientData() && result.drivers() != null) {
                RiskDriverBreakdown drivers = result.drivers();
                hasThermalExcursion = (drivers.accumulatedDegreeHours() != null && drivers.accumulatedDegreeHours().compareTo(BigDecimal.ZERO) > 0) ||
                        (drivers.maxObservedTemperature() != null && drivers.maxObservedTemperature().compareTo(tempMax) > 0) ||
                        (drivers.minObservedTemperature() != null && drivers.minObservedTemperature().compareTo(tempMin) < 0);
                hasHumidityExcursion = (drivers.humidityExcursionScore() != null && drivers.humidityExcursionScore().compareTo(BigDecimal.ZERO) > 0) ||
                        (drivers.maxObservedHumidity() != null && drivers.maxObservedHumidity().compareTo(humMax) > 0) ||
                        (drivers.minObservedHumidity() != null && drivers.minObservedHumidity().compareTo(humMin) < 0);
                hasExcursion = hasThermalExcursion || hasHumidityExcursion || (drivers.totalExcursionMinutes() > 0);
            }

            int unresCrit = 0;
            int unresHigh = 0;
            int unresOther = 0;
            int resCrit = 0;
            int resHigh = 0;
            int resOther = 0;

            if (alerts != null) {
                for (Alert a : alerts) {
                    if (a.getStatus() != AlertStatus.RESOLVED) {
                        if (a.getSeverity() == AlertSeverity.CRITICAL) {
                            unresCrit++;
                        } else if (a.getSeverity() == AlertSeverity.HIGH) {
                            unresHigh++;
                        } else {
                            unresOther++;
                        }
                    } else {
                        if (a.getSeverity() == AlertSeverity.CRITICAL) {
                            resCrit++;
                        } else if (a.getSeverity() == AlertSeverity.HIGH) {
                            resHigh++;
                        } else {
                            resOther++;
                        }
                    }
                }
            }

            SensorFacilityContext ctx = new SensorFacilityContext(
                    sensor, result, latestReading,
                    hasExcursion, hasThermalExcursion, hasHumidityExcursion,
                    isCurrentlyActiveUnsafe, isCurrentTempBreach, isCurrentHumBreach,
                    unresCrit, unresHigh, unresOther,
                    resCrit, resHigh, resOther
            );
            contexts.add(ctx);
            contextMap.put(sensor.getId(), ctx);

            if (result.sufficientData()) {
                sufficientCount++;
                validScores.add(result.overallRiskScore());
                if (result.overallRiskScore().compareTo(maxScore) > 0) {
                    maxScore = result.overallRiskScore();
                }
                summaries.add(new SensorRiskSummary(
                        sensor.getId(),
                        sensor.getSensorCode(),
                        sensor.getName(),
                        true,
                        result.overallRiskScore(),
                        result.riskTier()
                ));
            } else {
                insufficientCount++;
                summaries.add(new SensorRiskSummary(
                        sensor.getId(),
                        sensor.getSensorCode(),
                        sensor.getName(),
                        false,
                        null,
                        null
                ));
            }
        }

        BigDecimal facilityScore = null;
        RiskTier facilityTier = null;
        List<String> facilityRecs = new ArrayList<>();

        if (sufficientCount > 0) {
            BigDecimal sum = validScores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(BigDecimal.valueOf(validScores.size()), 4, RoundingMode.HALF_UP);

            // Conservative formula: 0.60 * maxScore + 0.40 * avgScore
            facilityScore = maxScore.multiply(new BigDecimal("0.60"))
                    .add(avg.multiply(new BigDecimal("0.40")))
                    .setScale(2, RoundingMode.HALF_UP)
                    .min(BigDecimal.valueOf(100.0));

            if (facilityScore.compareTo(riskProperties.getCriticalThreshold()) >= 0) {
                facilityTier = RiskTier.CRITICAL;
            } else if (facilityScore.compareTo(riskProperties.getElevatedThreshold()) >= 0) {
                facilityTier = RiskTier.ELEVATED;
            } else if (facilityScore.compareTo(riskProperties.getModerateThreshold()) >= 0) {
                facilityTier = RiskTier.MODERATE;
            } else {
                facilityTier = RiskTier.LOW;
            }
        }

        // Identify hotspots:
        // Selection rule: Surfaces sensors in ELEVATED or CRITICAL risk tiers, sensors with currently active
        // unsafe excursions, sensors with unresolved critical/high alerts, sensors with historical excursions
        // accompanied by critical/high alerts, and sensors with insufficient telemetry (to eliminate unmonitored blind spots).
        List<SensorRiskSummary> hotspots = summaries.stream()
                .filter(s -> {
                    if (!s.sufficientData()) {
                        return true; // Surfaced rather than silently omitted
                    }
                    if (s.riskScore() != null && s.riskScore().compareTo(riskProperties.getElevatedThreshold()) >= 0) {
                        return true;
                    }
                    SensorFacilityContext ctx = contextMap.get(s.sensorId());
                    if (ctx != null) {
                        if (ctx.isCurrentlyActiveUnsafe()) return true;
                        if (ctx.unresolvedCriticalAlerts() > 0 || ctx.unresolvedHighAlerts() > 0) return true;
                        if (ctx.hasExcursion() && ctx.totalHistoricalCriticalOrHighAlerts() > 0) return true;
                    }
                    return false;
                })
                .sorted((a, b) -> {
                    if (a.sufficientData() && b.sufficientData()) {
                        return b.riskScore().compareTo(a.riskScore());
                    } else if (a.sufficientData()) {
                        return -1; // Scored hotspots first
                    } else if (b.sufficientData()) {
                        return 1;
                    } else {
                        return a.sensorCode().compareTo(b.sensorCode());
                    }
                })
                .toList();

        // Facility-level recommendations synthesized from actual drivers, extremes, and alert status
        if (activeSensors.isEmpty()) {
            facilityRecs.add("Facility has no active sensors configured. Install or activate sensors to enable risk surveillance.");
        } else {
            // 1. Insufficient telemetry warning (surfaced prominently)
            if (insufficientCount > 0) {
                facilityRecs.add(String.format("%d of %d active sensors have insufficient telemetry in this %d-hour window. Investigate possible telemetry dropouts or unmonitored zones.",
                        insufficientCount, activeSensors.size(), windowHours));
            }

            // 2. Currently active unsafe conditions (only claimed if latest telemetry supports it)
            List<SensorFacilityContext> activeBreachSensors = contexts.stream()
                    .filter(SensorFacilityContext::isCurrentlyActiveUnsafe)
                    .toList();

            for (SensorFacilityContext ctx : activeBreachSensors) {
                BigDecimal latestTemp = ctx.latestReading() != null ? ctx.latestReading().getTemperatureCelsius() : null;
                BigDecimal latestHum = ctx.latestReading() != null ? ctx.latestReading().getHumidityPercentage() : null;
                BigDecimal peakTemp = (ctx.result().drivers() != null) ? ctx.result().drivers().maxObservedTemperature() : latestTemp;
                BigDecimal troughTemp = (ctx.result().drivers() != null) ? ctx.result().drivers().minObservedTemperature() : latestTemp;

                if (ctx.isCurrentlyTempBreached()) {
                    if (latestTemp != null && latestTemp.compareTo(tempMax) > 0) {
                        facilityRecs.add(String.format("Active temperature breach: Sensor %s (%s) is currently reporting %.2f °C, exceeding safe maximum of %.2f °C (peak was %.2f °C). Immediate climate adjustment required.",
                                ctx.sensor().getSensorCode(), ctx.sensor().getName(), latestTemp, tempMax, peakTemp));
                    } else if (latestTemp != null && latestTemp.compareTo(tempMin) < 0) {
                        facilityRecs.add(String.format("Active chilling breach: Sensor %s (%s) is currently reporting %.2f °C, below safe minimum of %.2f °C (trough was %.2f °C). Immediate cooling system check required.",
                                ctx.sensor().getSensorCode(), ctx.sensor().getName(), latestTemp, tempMin, troughTemp));
                    }
                }
                if (ctx.isCurrentlyHumBreached()) {
                    facilityRecs.add(String.format("Active humidity breach: Sensor %s (%s) is currently reporting %.2f %%, outside safe operational range (%.2f - %.2f %%). Immediate ventilation check required.",
                            ctx.sensor().getSensorCode(), ctx.sensor().getName(), latestHum, humMin, humMax));
                }
            }

            // 3. Historical excursions where latest telemetry has returned to normal range
            List<SensorFacilityContext> historicalExcursionSensors = contexts.stream()
                    .filter(ctx -> ctx.hasExcursion() && !ctx.isCurrentlyActiveUnsafe())
                    .toList();

            for (SensorFacilityContext ctx : historicalExcursionSensors) {
                BigDecimal latestTemp = ctx.latestReading() != null ? ctx.latestReading().getTemperatureCelsius() : null;
                BigDecimal peakTemp = (ctx.result().drivers() != null) ? ctx.result().drivers().maxObservedTemperature() : null;
                BigDecimal troughTemp = (ctx.result().drivers() != null) ? ctx.result().drivers().minObservedTemperature() : null;
                BigDecimal degHours = (ctx.result().drivers() != null && ctx.result().drivers().accumulatedDegreeHours() != null)
                        ? ctx.result().drivers().accumulatedDegreeHours() : BigDecimal.ZERO;

                if (ctx.hasThermalExcursion()) {
                    String breachDetail;
                    if (peakTemp != null && peakTemp.compareTo(tempMax) > 0) {
                        breachDetail = String.format("peaked at %.2f °C (safe maximum is %.2f °C)", peakTemp, tempMax);
                    } else {
                        breachDetail = String.format("dropped to %.2f °C (safe minimum is %.2f °C)", troughTemp, tempMin);
                    }
                    facilityRecs.add(String.format("Historical excursion detected: Sensor %s (%s) %s with %.2f degree-hours accumulated, but latest telemetry (%.2f °C) indicates readings have returned within safe operational limits. Inspect inventory for cumulative thermal exposure damage.",
                            ctx.sensor().getSensorCode(), ctx.sensor().getName(), breachDetail, degHours, latestTemp));
                } else if (ctx.hasHumidityExcursion()) {
                    BigDecimal latestHum = ctx.latestReading() != null ? ctx.latestReading().getHumidityPercentage() : null;
                    facilityRecs.add(String.format("Historical humidity excursion detected: Sensor %s (%s) experienced out-of-range humidity, but latest telemetry (%.2f %%) is currently within safe limits.",
                            ctx.sensor().getSensorCode(), ctx.sensor().getName(), latestHum));
                }
            }

            // 4. Alert status across facility (unresolved vs resolved)
            int totalUnresolvedCritical = contexts.stream().mapToInt(SensorFacilityContext::unresolvedCriticalAlerts).sum();
            int totalUnresolvedHigh = contexts.stream().mapToInt(SensorFacilityContext::unresolvedHighAlerts).sum();
            int totalUnresolvedOther = contexts.stream().mapToInt(SensorFacilityContext::unresolvedOtherAlerts).sum();
            int totalUnresolved = totalUnresolvedCritical + totalUnresolvedHigh + totalUnresolvedOther;

            int totalResolvedCritical = contexts.stream().mapToInt(SensorFacilityContext::resolvedCriticalAlerts).sum();
            int totalResolvedHigh = contexts.stream().mapToInt(SensorFacilityContext::resolvedHighAlerts).sum();
            int totalHistoricalCriticalOrHigh = totalResolvedCritical + totalResolvedHigh;

            if (totalUnresolved > 0) {
                facilityRecs.add(String.format("Active alert status: %d unresolved alert(s) across facility (%d critical, %d high). Review alert log and prioritize technician dispatch.",
                        totalUnresolved, totalUnresolvedCritical, totalUnresolvedHigh));
            } else if (totalHistoricalCriticalOrHigh > 0) {
                facilityRecs.add(String.format("Historical alert status: %d critical/high alert(s) occurred during this observation window and have been marked resolved.",
                        totalHistoricalCriticalOrHigh));
            }

            // 5. Overall facility risk tier & hotspot summary
            if (facilityTier == RiskTier.CRITICAL || facilityTier == RiskTier.ELEVATED) {
                facilityRecs.add(String.format("Facility risk is %s. %d sensor hotspot(s) require immediate operational attention.",
                        facilityTier, hotspots.size()));
            } else if (facilityTier == RiskTier.MODERATE && !hotspots.isEmpty()) {
                facilityRecs.add(String.format("Facility risk is MODERATE. Monitor %d highlighted sensor hotspot(s) to prevent condition escalation.",
                        hotspots.size()));
            }

            // 6. Stable check: Never return blanket stable statement if any sensor has excursions, active breaches, unresolved alerts, or insufficient data
            boolean anyExcursion = contexts.stream().anyMatch(SensorFacilityContext::hasExcursion);
            boolean anyActiveBreach = !activeBreachSensors.isEmpty();
            boolean anyUnresolvedAlert = totalUnresolved > 0;
            boolean anyHistoricalCriticalOrHighAlert = totalHistoricalCriticalOrHigh > 0;

            if (!anyExcursion && !anyActiveBreach && !anyUnresolvedAlert && !anyHistoricalCriticalOrHighAlert && insufficientCount == 0) {
                facilityRecs.add("All monitored zones are stable within normal safe operational boundaries.");
            }
        }

        String aggregationDesc = "Facility risk score is calculated using conservative hotspot weighting: " +
                "60% weight on maximum sensor risk score + 40% weight on average score across sensors with sufficient data. " +
                "Hotspot selection rule: surfaces sensors in ELEVATED or CRITICAL risk tiers, sensors with active excursions " +
                "or unresolved high/critical alerts, historical excursions with critical alerts, and sensors with insufficient " +
                "telemetry to avoid unmonitored blind spots.";

        return new FacilityRiskAssessmentResponse(
                facility.getId(),
                facility.getName(),
                facility.getFacilityType(),
                facility.getLocation(),
                windowHours,
                windowStart,
                windowEnd,
                activeSensors.size(),
                sufficientCount,
                insufficientCount,
                facilityScore,
                facilityTier,
                hotspots,
                sensorAssessments,
                facilityRecs,
                aggregationDesc,
                RiskAssessmentResult.EXPERIMENTAL_DISCLAIMER
        );
    }

    private void validateWindowHours(int windowHours) {
        if (windowHours <= 0) {
            throw new IllegalArgumentException("windowHours must be a positive integer greater than 0");
        }
        if (windowHours > MAX_WINDOW_HOURS) {
            throw new IllegalArgumentException(String.format("windowHours cannot exceed %d (30 days)", MAX_WINDOW_HOURS));
        }
    }

    /**
     * Assesses risk for a given collection of telemetry readings and alerts within an observation window.
     *
     * @param readings Collection of sensor readings in the window (may arrive in any chronological order)
     * @param alerts   Alerts logged for the sensor or facility in the window
     * @param windowStart Start of evaluation window
     * @param windowEnd   End of evaluation window
     * @return RiskAssessmentResult containing score, tier, drivers, and recommendations
     */
    public RiskAssessmentResult assessRisk(List<SensorReading> readings,
                                           List<Alert> alerts,
                                           LocalDateTime windowStart,
                                           LocalDateTime windowEnd) {
        if (readings == null || readings.size() < riskProperties.getMinReadingsRequired()) {
            int count = (readings != null) ? readings.size() : 0;
            return new RiskAssessmentResult(
                    false,
                    null,
                    null,
                    String.format("Insufficient telemetry data: %d readings found; minimum required is %d", count, riskProperties.getMinReadingsRequired()),
                    null,
                    List.of("Ensure the sensor is active and reporting telemetry consistently before generating a risk score."),
                    windowStart,
                    windowEnd,
                    count,
                    RiskAssessmentResult.EXPERIMENTAL_DISCLAIMER
            );
        }

        // 1. Sort readings chronologically and deduplicate identical timestamps
        List<SensorReading> sortedReadings = sanitizeAndSortReadings(readings);
        if (sortedReadings.size() < riskProperties.getMinReadingsRequired()) {
            return new RiskAssessmentResult(
                    false,
                    null,
                    null,
                    String.format("Insufficient distinct telemetry data after deduplication: %d readings found; minimum required is %d",
                            sortedReadings.size(), riskProperties.getMinReadingsRequired()),
                    null,
                    List.of("Telemetry contained duplicate timestamps with insufficient distinct intervals."),
                    windowStart,
                    windowEnd,
                    sortedReadings.size(),
                    RiskAssessmentResult.EXPERIMENTAL_DISCLAIMER
            );
        }

        // 2. Numerical Integration for Thermal & Humidity Excursions
        // Chosen interval approximation:
        // Trapezoidal integration between consecutive readings (t_{i-1}, v_{i-1}) and (t_i, v_i).
        // If the interval between consecutive readings exceeds maxIntervalMinutes, the system assumes
        // an unmonitored disconnection gap rather than unbroken continuous exposure. In that case,
        // exposure is only attributed up to half the interval (capped at maxIntervalMinutes) to avoid
        // spurious integration explosion over prolonged sensor downtime.
        BigDecimal tempMin = thresholdProperties.getTemperatureMin();
        BigDecimal tempMax = thresholdProperties.getTemperatureMax();
        BigDecimal humMin = thresholdProperties.getHumidityMin();
        BigDecimal humMax = thresholdProperties.getHumidityMax();

        BigDecimal accumulatedDegreeHours = BigDecimal.ZERO;
        long totalExcursionMinutes = 0;
        long currentContinuousMinutes = 0;
        long maxContinuousMinutes = 0;

        BigDecimal maxObservedTemp = null;
        BigDecimal minObservedTemp = null;
        BigDecimal maxObservedHum = null;
        BigDecimal minObservedHum = null;

        BigDecimal accumulatedHumidityExcursionPercentHours = BigDecimal.ZERO;

        for (int i = 0; i < sortedReadings.size(); i++) {
            SensorReading current = sortedReadings.get(i);

            // Track min/max observed extremes
            if (current.getTemperatureCelsius() != null) {
                BigDecimal t = current.getTemperatureCelsius();
                if (maxObservedTemp == null || t.compareTo(maxObservedTemp) > 0) maxObservedTemp = t;
                if (minObservedTemp == null || t.compareTo(minObservedTemp) < 0) minObservedTemp = t;
            }
            if (current.getHumidityPercentage() != null) {
                BigDecimal h = current.getHumidityPercentage();
                if (maxObservedHum == null || h.compareTo(maxObservedHum) > 0) maxObservedHum = h;
                if (minObservedHum == null || h.compareTo(minObservedHum) < 0) minObservedHum = h;
            }

            if (i > 0) {
                SensorReading prev = sortedReadings.get(i - 1);
                long minutesBetween = Duration.between(prev.getRecordedAt(), current.getRecordedAt()).toMinutes();

                if (minutesBetween > 0) {
                    // Cap interval to avoid multiplying across telemetry blackouts
                    boolean gapExceeded = minutesBetween > riskProperties.getMaxIntervalMinutes();
                    long effectiveMinutes = gapExceeded ? riskProperties.getMaxIntervalMinutes() : minutesBetween;
                    BigDecimal intervalHours = BigDecimal.valueOf(effectiveMinutes).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);

                    // Temperature evaluation
                    BigDecimal prevTempExc = calculateTempExcursion(prev.getTemperatureCelsius(), tempMin, tempMax);
                    BigDecimal currTempExc = calculateTempExcursion(current.getTemperatureCelsius(), tempMin, tempMax);

                    if (prevTempExc.compareTo(BigDecimal.ZERO) > 0 || currTempExc.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal avgTempExc = prevTempExc.add(currTempExc).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
                        accumulatedDegreeHours = accumulatedDegreeHours.add(avgTempExc.multiply(intervalHours));
                    }

                    // Humidity evaluation
                    BigDecimal prevHumExc = calculateHumExcursion(prev.getHumidityPercentage(), humMin, humMax);
                    BigDecimal currHumExc = calculateHumExcursion(current.getHumidityPercentage(), humMin, humMax);

                    if (prevHumExc.compareTo(BigDecimal.ZERO) > 0 || currHumExc.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal avgHumExc = prevHumExc.add(currHumExc).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
                        accumulatedHumidityExcursionPercentHours = accumulatedHumidityExcursionPercentHours.add(avgHumExc.multiply(intervalHours));
                    }

                    // Duration tracking
                    boolean inExcursion = (currTempExc.compareTo(BigDecimal.ZERO) > 0) || (currHumExc.compareTo(BigDecimal.ZERO) > 0);
                    if (inExcursion) {
                        totalExcursionMinutes += effectiveMinutes;
                        if (gapExceeded) {
                            currentContinuousMinutes = effectiveMinutes; // Reset continuity after gap
                        } else {
                            currentContinuousMinutes += effectiveMinutes;
                        }
                        if (currentContinuousMinutes > maxContinuousMinutes) {
                            maxContinuousMinutes = currentContinuousMinutes;
                        }
                    } else {
                        currentContinuousMinutes = 0;
                    }
                }
            }
        }

        // 3. Sub-Scores Calculation (each normalized to 0 - 100)
        // Thermal score: 10 degree-hours mapped towards 100
        BigDecimal thermalScore = accumulatedDegreeHours.multiply(BigDecimal.valueOf(10.0))
                .setScale(2, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100.0));

        // Humidity score: 50 %-hours mapped towards 100
        BigDecimal humidityScore = accumulatedHumidityExcursionPercentHours.multiply(BigDecimal.valueOf(2.0))
                .setScale(2, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100.0));

        // Duration score: 180 continuous minutes (3h) mapped towards 100
        BigDecimal durationScore = BigDecimal.valueOf(maxContinuousMinutes)
                .divide(BigDecimal.valueOf(180.0), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100.0))
                .setScale(2, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100.0));

        // 4. Alert Penalties with Incident Clustering
        // Group alerts of same type within close timeframe (e.g. 120m) into single incident to prevent runaway stacking
        AlertEvaluationResult alertEval = evaluateAlerts(alerts);
        BigDecimal alertScore = alertEval.score().min(BigDecimal.valueOf(100.0));

        // 5. Total Weighted Risk Score
        BigDecimal overallScore = riskProperties.getWeightThermalExcursions().multiply(thermalScore)
                .add(riskProperties.getWeightHumidityExcursions().multiply(humidityScore))
                .add(riskProperties.getWeightExcursionDuration().multiply(durationScore))
                .add(riskProperties.getWeightRecentAlerts().multiply(alertScore))
                .setScale(2, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.valueOf(100.0));

        // Determine qualitative RiskTier
        RiskTier tier;
        if (overallScore.compareTo(riskProperties.getCriticalThreshold()) >= 0) {
            tier = RiskTier.CRITICAL;
        } else if (overallScore.compareTo(riskProperties.getElevatedThreshold()) >= 0) {
            tier = RiskTier.ELEVATED;
        } else if (overallScore.compareTo(riskProperties.getModerateThreshold()) >= 0) {
            tier = RiskTier.MODERATE;
        } else {
            tier = RiskTier.LOW;
        }

        // 6. Actionable, Evidence-Grounded Recommendations
        SensorReading latestReading = sortedReadings.isEmpty() ? null : sortedReadings.get(sortedReadings.size() - 1);
        List<String> recommendations = generateRecommendations(
                accumulatedDegreeHours, maxObservedTemp, minObservedTemp,
                maxObservedHum, minObservedHum, tempMin, tempMax, humMin, humMax,
                maxContinuousMinutes, latestReading, alertEval
        );

        RiskDriverBreakdown drivers = new RiskDriverBreakdown(
                thermalScore,
                humidityScore,
                durationScore,
                alertScore,
                accumulatedDegreeHours.setScale(2, RoundingMode.HALF_UP),
                totalExcursionMinutes,
                maxContinuousMinutes,
                maxObservedTemp,
                minObservedTemp,
                maxObservedHum,
                minObservedHum,
                alertEval.evaluatedCount(),
                alertEval.suppressedDuplicatesCount()
        );

        return new RiskAssessmentResult(
                true,
                overallScore,
                tier,
                String.format("Assessment completed successfully: evaluated %d readings and %d incident clusters",
                        sortedReadings.size(), alertEval.incidentClustersCount()),
                drivers,
                recommendations,
                windowStart,
                windowEnd,
                sortedReadings.size(),
                RiskAssessmentResult.EXPERIMENTAL_DISCLAIMER
        );
    }

    private List<SensorReading> sanitizeAndSortReadings(List<SensorReading> readings) {
        List<SensorReading> copy = new ArrayList<>(readings);
        copy.sort(Comparator.comparing(SensorReading::getRecordedAt));

        // Deduplicate consecutive entries with exact same timestamp
        List<SensorReading> distinct = new ArrayList<>();
        LocalDateTime lastTime = null;
        for (SensorReading r : copy) {
            if (lastTime == null || !r.getRecordedAt().equals(lastTime)) {
                distinct.add(r);
                lastTime = r.getRecordedAt();
            }
        }
        return distinct;
    }

    private BigDecimal calculateTempExcursion(BigDecimal temp, BigDecimal min, BigDecimal max) {
        if (temp == null) return BigDecimal.ZERO;
        if (temp.compareTo(max) > 0) {
            return temp.subtract(max);
        } else if (temp.compareTo(min) < 0) {
            return min.subtract(temp);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateHumExcursion(BigDecimal hum, BigDecimal min, BigDecimal max) {
        if (hum == null) return BigDecimal.ZERO;
        if (hum.compareTo(max) > 0) {
            return hum.subtract(max);
        } else if (hum.compareTo(min) < 0) {
            return min.subtract(hum);
        }
        return BigDecimal.ZERO;
    }

    private AlertEvaluationResult evaluateAlerts(List<Alert> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return new AlertEvaluationResult(BigDecimal.ZERO, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        // Sort alerts chronologically
        List<Alert> sorted = new ArrayList<>(alerts);
        sorted.sort(Comparator.comparing(Alert::getCreatedAt));

        // Cluster alerts of the same AlertType occurring within 120 minutes of each other
        Map<AlertType, List<Alert>> byType = new HashMap<>();
        for (Alert a : sorted) {
            byType.computeIfAbsent(a.getAlertType(), k -> new ArrayList<>()).add(a);
        }

        BigDecimal totalPenalty = BigDecimal.ZERO;
        int suppressedDuplicates = 0;
        int unresolvedCritical = 0;
        int unresolvedHigh = 0;
        int unresolvedTotal = 0;
        int historicalCritical = 0;
        int historicalHigh = 0;
        int incidentClusters = 0;

        for (Alert alert : sorted) {
            boolean isUnresolved = alert.getStatus() != AlertStatus.RESOLVED;
            if (isUnresolved) {
                unresolvedTotal++;
                if (alert.getSeverity() == AlertSeverity.CRITICAL) {
                    unresolvedCritical++;
                } else if (alert.getSeverity() == AlertSeverity.HIGH) {
                    unresolvedHigh++;
                }
            } else {
                if (alert.getSeverity() == AlertSeverity.CRITICAL) {
                    historicalCritical++;
                } else if (alert.getSeverity() == AlertSeverity.HIGH) {
                    historicalHigh++;
                }
            }
        }

        for (Map.Entry<AlertType, List<Alert>> entry : byType.entrySet()) {
            List<Alert> list = entry.getValue();
            Alert currentClusterLead = null;
            AlertSeverity clusterMaxSeverity = null;

            for (Alert alert : list) {
                if (currentClusterLead == null) {
                    currentClusterLead = alert;
                    clusterMaxSeverity = alert.getSeverity();
                    incidentClusters++;
                } else {
                    long minutes = Duration.between(currentClusterLead.getCreatedAt(), alert.getCreatedAt()).toMinutes();
                    if (minutes <= 120) {
                        // Part of ongoing incident
                        suppressedDuplicates++;
                        if (alert.getSeverity().ordinal() > clusterMaxSeverity.ordinal()) {
                            clusterMaxSeverity = alert.getSeverity();
                        }
                    } else {
                        // Finalize previous cluster
                        totalPenalty = totalPenalty.add(severityToPenalty(clusterMaxSeverity));
                        // Start new cluster
                        currentClusterLead = alert;
                        clusterMaxSeverity = alert.getSeverity();
                        incidentClusters++;
                    }
                }
            }
            if (clusterMaxSeverity != null) {
                totalPenalty = totalPenalty.add(severityToPenalty(clusterMaxSeverity));
            }
        }

        return new AlertEvaluationResult(
                totalPenalty, alerts.size(), suppressedDuplicates,
                unresolvedCritical, unresolvedHigh, unresolvedTotal,
                historicalCritical, historicalHigh, incidentClusters
        );
    }

    private BigDecimal severityToPenalty(AlertSeverity severity) {
        if (severity == null) return BigDecimal.ZERO;
        return switch (severity) {
            case CRITICAL -> BigDecimal.valueOf(50.0);
            case HIGH -> BigDecimal.valueOf(35.0);
            case MEDIUM -> BigDecimal.valueOf(20.0);
            case LOW -> BigDecimal.valueOf(10.0);
        };
    }

    private List<String> generateRecommendations(BigDecimal degreeHours,
                                                 BigDecimal maxTemp,
                                                 BigDecimal minTemp,
                                                 BigDecimal maxHum,
                                                 BigDecimal minHum,
                                                 BigDecimal tempMin,
                                                 BigDecimal tempMax,
                                                 BigDecimal humMin,
                                                 BigDecimal humMax,
                                                 long maxContinuousMinutes,
                                                 SensorReading latestReading,
                                                 AlertEvaluationResult alertEval) {
        List<String> recs = new ArrayList<>();

        BigDecimal latestTemp = latestReading != null ? latestReading.getTemperatureCelsius() : null;
        BigDecimal latestHum = latestReading != null ? latestReading.getHumidityPercentage() : null;

        boolean hasTempExcursion = (degreeHours.compareTo(BigDecimal.ZERO) > 0) ||
                (maxTemp != null && maxTemp.compareTo(tempMax) > 0) ||
                (minTemp != null && minTemp.compareTo(tempMin) < 0);
        boolean hasHumExcursion = (maxHum != null && maxHum.compareTo(humMax) > 0) ||
                (minHum != null && minHum.compareTo(humMin) < 0);
        boolean hasAnyExcursion = hasTempExcursion || hasHumExcursion || maxContinuousMinutes > 0;
        boolean hasAnyAlerts = alertEval.evaluatedCount() > 0;

        if (!hasAnyExcursion && !hasAnyAlerts) {
            recs.add("Environmental parameters stayed strictly within safe thresholds throughout this observation window.");
            return recs;
        }

        // Temperature recommendations
        if (maxTemp != null && maxTemp.compareTo(tempMax) > 0) {
            if (latestTemp != null && latestTemp.compareTo(tempMax) > 0) {
                recs.add(String.format("Active temperature breach: Current temperature is %.2f °C, exceeding safe maximum of %.2f °C (peak was %.2f °C). Verify cooling system airflow and thermal door seals.",
                        latestTemp, tempMax, maxTemp));
            } else {
                recs.add(String.format("Observed temperature peaked at %.2f °C (safe maximum is %.2f °C). Latest telemetry (%.2f °C) has returned within safe limits, but verify cooling system airflow and check inventory for thermal degradation.",
                        maxTemp, tempMax, latestTemp));
            }
        }

        if (minTemp != null && minTemp.compareTo(tempMin) < 0) {
            if (latestTemp != null && latestTemp.compareTo(tempMin) < 0) {
                recs.add(String.format("Active chilling breach: Current temperature is %.2f °C, below safe minimum of %.2f °C (trough was %.2f °C). Check evaporator defrost cycles to prevent chilling damage.",
                        latestTemp, tempMin, minTemp));
            } else {
                recs.add(String.format("Observed temperature dropped to %.2f °C (safe minimum is %.2f °C). Latest telemetry (%.2f °C) has returned within safe limits, but check evaporator defrost cycles to prevent chilling damage.",
                        minTemp, tempMin, latestTemp));
            }
        }

        if (degreeHours.compareTo(BigDecimal.valueOf(5.0)) > 0) {
            recs.add(String.format("Accumulated %.2f thermal degree-hours of excursion. Immediate inspection of temperature-sensitive inventory is recommended.",
                    degreeHours));
        }

        // Humidity recommendations
        if (maxHum != null && maxHum.compareTo(humMax) > 0) {
            if (latestHum != null && latestHum.compareTo(humMax) > 0) {
                recs.add(String.format("Active high humidity breach: Relative humidity peaked at %.2f %% (upper safe limit is %.2f %%, current %.2f %%). Inspect for moisture condensation and ensure adequate chamber ventilation.",
                        maxHum, humMax, latestHum));
            } else {
                recs.add(String.format("Relative humidity peaked at %.2f %% (upper safe limit is %.2f %%). Latest telemetry (%.2f %%) is within safe limits. Inspect for moisture condensation and ensure adequate chamber ventilation.",
                        maxHum, humMax, latestHum));
            }
        }

        if (minHum != null && minHum.compareTo(humMin) < 0) {
            if (latestHum != null && latestHum.compareTo(humMin) < 0) {
                recs.add(String.format("Active low humidity breach: Relative humidity fell to %.2f %% (lower safe limit is %.2f %%, current %.2f %%). Low humidity accelerates commodity moisture loss and desiccation.",
                        minHum, humMin, latestHum));
            } else {
                recs.add(String.format("Relative humidity fell to %.2f %% (lower safe limit is %.2f %%). Latest telemetry (%.2f %%) is within safe limits. Low humidity accelerates commodity moisture loss and desiccation.",
                        minHum, humMin, latestHum));
            }
        }

        if (maxContinuousMinutes >= 180) {
            recs.add(String.format("Prolonged continuous excursion detected (lasting %d minutes). Prompt operational review required.",
                    maxContinuousMinutes));
        }

        // Alert recommendations
        if (alertEval.unresolvedTotalCount() > 0) {
            recs.add(String.format("Active alert status: %d unresolved alert(s) (%d critical, %d high) logged in this period. Review alert log and prioritize technician dispatch.",
                    alertEval.unresolvedTotalCount(), alertEval.unresolvedCriticalCount(), alertEval.unresolvedHighCount()));
        } else if (alertEval.historicalCriticalCount() > 0 || alertEval.historicalHighCount() > 0) {
            recs.add(String.format("Historical alerts evaluated: %d critical/high alert(s) occurred during this observation window and have been marked resolved.",
                    alertEval.historicalCriticalCount() + alertEval.historicalHighCount()));
        } else if (alertEval.evaluatedCount() > 0) {
            recs.add(String.format("Historical alerts evaluated: %d alert(s) logged in this period have been marked resolved.",
                    alertEval.evaluatedCount()));
        }

        return recs;
    }

    private record AlertEvaluationResult(
            BigDecimal score,
            int evaluatedCount,
            int suppressedDuplicatesCount,
            int unresolvedCriticalCount,
            int unresolvedHighCount,
            int unresolvedTotalCount,
            int historicalCriticalCount,
            int historicalHighCount,
            int incidentClustersCount
    ) {
    }

    private record SensorFacilityContext(
            Sensor sensor,
            RiskAssessmentResult result,
            SensorReading latestReading,
            boolean hasExcursion,
            boolean hasThermalExcursion,
            boolean hasHumidityExcursion,
            boolean isCurrentlyActiveUnsafe,
            boolean isCurrentlyTempBreached,
            boolean isCurrentlyHumBreached,
            int unresolvedCriticalAlerts,
            int unresolvedHighAlerts,
            int unresolvedOtherAlerts,
            int resolvedCriticalAlerts,
            int resolvedHighAlerts,
            int resolvedOtherAlerts
    ) {
        int totalUnresolvedAlerts() {
            return unresolvedCriticalAlerts + unresolvedHighAlerts + unresolvedOtherAlerts;
        }

        int totalHistoricalCriticalOrHighAlerts() {
            return resolvedCriticalAlerts + resolvedHighAlerts;
        }
    }
}
