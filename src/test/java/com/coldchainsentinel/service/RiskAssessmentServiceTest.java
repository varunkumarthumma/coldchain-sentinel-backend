package com.coldchainsentinel.service;

import com.coldchainsentinel.config.AlertThresholdProperties;
import com.coldchainsentinel.config.RiskAssessmentProperties;
import com.coldchainsentinel.dto.RiskAssessmentResult;
import com.coldchainsentinel.model.*;
import com.coldchainsentinel.repository.AlertRepository;
import com.coldchainsentinel.repository.SensorReadingRepository;
import com.coldchainsentinel.repository.SensorRepository;
import com.coldchainsentinel.repository.StorageFacilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskAssessmentServiceTest {

    private RiskAssessmentProperties riskProperties;
    private AlertThresholdProperties thresholdProperties;
    private RiskAssessmentService riskAssessmentService;

    private Sensor testSensor;
    private StorageFacility testFacility;

    @BeforeEach
    void setUp() {
        riskProperties = new RiskAssessmentProperties();
        riskProperties.setWeightThermalExcursions(new BigDecimal("0.35"));
        riskProperties.setWeightHumidityExcursions(new BigDecimal("0.25"));
        riskProperties.setWeightExcursionDuration(new BigDecimal("0.20"));
        riskProperties.setWeightRecentAlerts(new BigDecimal("0.20"));
        riskProperties.setMinReadingsRequired(3);
        riskProperties.setMaxIntervalMinutes(120);
        riskProperties.setModerateThreshold(new BigDecimal("25.00"));
        riskProperties.setElevatedThreshold(new BigDecimal("55.00"));
        riskProperties.setCriticalThreshold(new BigDecimal("80.00"));
        riskProperties.validate();

        thresholdProperties = new AlertThresholdProperties();
        thresholdProperties.setTemperatureMin(new BigDecimal("0.00"));
        thresholdProperties.setTemperatureMax(new BigDecimal("8.00"));
        thresholdProperties.setTemperatureCriticalDelta(new BigDecimal("4.00"));
        thresholdProperties.setHumidityMin(new BigDecimal("65.00"));
        thresholdProperties.setHumidityMax(new BigDecimal("95.00"));
        thresholdProperties.setHumidityCriticalDelta(new BigDecimal("10.00"));

        SensorRepository sensorRepository = org.mockito.Mockito.mock(SensorRepository.class);
        StorageFacilityRepository storageFacilityRepository = org.mockito.Mockito.mock(StorageFacilityRepository.class);
        SensorReadingRepository sensorReadingRepository = org.mockito.Mockito.mock(SensorReadingRepository.class);
        AlertRepository alertRepository = org.mockito.Mockito.mock(AlertRepository.class);

        riskAssessmentService = new RiskAssessmentService(
                riskProperties,
                thresholdProperties,
                sensorRepository,
                storageFacilityRepository,
                sensorReadingRepository,
                alertRepository
        );

        testFacility = new StorageFacility("Central Hub", "Zone 1", new BigDecimal("500"), FacilityType.COLD_STORAGE);
        testFacility.setId(1L);
        testSensor = new Sensor("SN-TEST-01", "Sensor 01", SensorType.TEMPERATURE_HUMIDITY, testFacility);
        testSensor.setId(10L);
    }

    private SensorReading reading(double temp, double hum, LocalDateTime time) {
        return new SensorReading(
                testSensor,
                BigDecimal.valueOf(temp),
                BigDecimal.valueOf(hum),
                time,
                ReadingDataSource.DEVICE
        );
    }

    @Test
    @DisplayName("No excursions results in zero risk score and LOW tier")
    void assessRisk_NoExcursions_ReturnsZeroScoreAndLowTier() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 11, 10, 0);
        List<SensorReading> readings = List.of(
                reading(4.0, 75.0, base),
                reading(4.5, 78.0, base.plusMinutes(30)),
                reading(4.2, 76.0, base.plusMinutes(60))
        );

        RiskAssessmentResult result = riskAssessmentService.assessRisk(readings, Collections.emptyList(), base, base.plusHours(2));

        assertThat(result.sufficientData()).isTrue();
        assertThat(result.overallRiskScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.riskTier()).isEqualTo(RiskTier.LOW);
        assertThat(result.drivers().accumulatedDegreeHours()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.recommendations()).anyMatch(r -> r.contains("strictly within safe thresholds"));
        assertThat(result.disclaimer()).isEqualTo(RiskAssessmentResult.EXPERIMENTAL_DISCLAIMER);
    }

    @Test
    @DisplayName("High temperature excursions elevate score and assign appropriate tier")
    void assessRisk_HighTemperatureExcursions_ElevatesScore() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 11, 10, 0);
        // Exceeds max 8.00 °C: 12.00, 14.00, 13.00 for 2 hours (120 minutes)
        List<SensorReading> readings = List.of(
                reading(12.0, 75.0, base),
                reading(14.0, 75.0, base.plusMinutes(60)),
                reading(13.0, 75.0, base.plusMinutes(120))
        );

        RiskAssessmentResult result = riskAssessmentService.assessRisk(readings, Collections.emptyList(), base, base.plusHours(3));

        assertThat(result.sufficientData()).isTrue();
        assertThat(result.overallRiskScore()).isGreaterThan(new BigDecimal("15.00"));
        assertThat(result.drivers().accumulatedDegreeHours()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.drivers().maxObservedTemperature()).isEqualByComparingTo(new BigDecimal("14.00"));
        assertThat(result.recommendations()).anyMatch(r -> r.contains("Verify cooling system airflow"));
    }

    @Test
    @DisplayName("Low temperature excursions (chilling risk) contribute to degree-hours")
    void assessRisk_LowTemperatureExcursions_ContributesToDegreeHours() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 11, 10, 0);
        // Minimum is 0.00 °C; readings are -3.00 and -2.00
        List<SensorReading> readings = List.of(
                reading(-3.0, 75.0, base),
                reading(-2.5, 75.0, base.plusMinutes(30)),
                reading(-2.0, 75.0, base.plusMinutes(60))
        );

        RiskAssessmentResult result = riskAssessmentService.assessRisk(readings, Collections.emptyList(), base, base.plusHours(2));

        assertThat(result.sufficientData()).isTrue();
        assertThat(result.drivers().accumulatedDegreeHours()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.drivers().minObservedTemperature()).isEqualByComparingTo(new BigDecimal("-3.00"));
        assertThat(result.recommendations()).anyMatch(r -> r.contains("evaporator defrost cycles"));
    }

    @Test
    @DisplayName("Low humidity excursions (desiccation) increase humidity sub-score")
    void assessRisk_LowHumidityExcursions_IncreasesHumidityScore() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 11, 10, 0);
        // Safe humidity min is 65.00; readings are 45.00 and 50.00
        List<SensorReading> readings = List.of(
                reading(4.0, 45.0, base),
                reading(4.0, 48.0, base.plusMinutes(30)),
                reading(4.0, 50.0, base.plusMinutes(60))
        );

        RiskAssessmentResult result = riskAssessmentService.assessRisk(readings, Collections.emptyList(), base, base.plusHours(2));

        assertThat(result.sufficientData()).isTrue();
        assertThat(result.drivers().humidityExcursionScore()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.drivers().minObservedHumidity()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(result.recommendations()).anyMatch(r -> r.contains("Low humidity accelerates commodity moisture loss"));
    }

    @Test
    @DisplayName("High humidity excursions (condensation risk) increase humidity sub-score")
    void assessRisk_HighHumidityExcursions_IncreasesHumidityScore() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 11, 10, 0);
        // Safe humidity max is 95.00; readings are 99.00 and 98.00
        List<SensorReading> readings = List.of(
                reading(4.0, 99.0, base),
                reading(4.0, 98.0, base.plusMinutes(30)),
                reading(4.0, 98.5, base.plusMinutes(60))
        );

        RiskAssessmentResult result = riskAssessmentService.assessRisk(readings, Collections.emptyList(), base, base.plusHours(2));

        assertThat(result.sufficientData()).isTrue();
        assertThat(result.drivers().humidityExcursionScore()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.recommendations()).anyMatch(r -> r.contains("Relative humidity peaked"));
    }

    @Test
    @DisplayName("Extreme values and multi-factor excursions cap overall score at 100.00 and assign CRITICAL tier")
    void assessRisk_ExtremeExcursions_CapsAtOneHundredAndAssignsCritical() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 11, 10, 0);
        // Extreme temperature 28 °C, extreme humidity 20 %, plus multiple critical alerts
        List<SensorReading> readings = List.of(
                reading(28.0, 20.0, base),
                reading(30.0, 18.0, base.plusMinutes(60)),
                reading(32.0, 15.0, base.plusMinutes(120)),
                reading(35.0, 15.0, base.plusMinutes(180))
        );

        Alert criticalAlert1 = new Alert(testSensor, testFacility, AlertType.HIGH_TEMPERATURE, AlertSeverity.CRITICAL, "Critical temp", new BigDecimal("30"), new BigDecimal("8"));
        criticalAlert1.setCreatedAt(base.plusMinutes(10));
        Alert criticalAlert2 = new Alert(testSensor, testFacility, AlertType.LOW_HUMIDITY, AlertSeverity.CRITICAL, "Critical low hum", new BigDecimal("18"), new BigDecimal("65"));
        criticalAlert2.setCreatedAt(base.plusMinutes(15));

        RiskAssessmentResult result = riskAssessmentService.assessRisk(readings, List.of(criticalAlert1, criticalAlert2), base, base.plusHours(4));

        assertThat(result.sufficientData()).isTrue();
        assertThat(result.overallRiskScore()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.riskTier()).isEqualTo(RiskTier.CRITICAL);
    }

    @Test
    @DisplayName("Readings arriving in reverse chronological order are sorted and assessed correctly")
    void assessRisk_ReadingsInReverseOrder_ProducesAccurateAssessment() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 11, 10, 0);
        // Descending order (newest-first)
        List<SensorReading> descendingReadings = List.of(
                reading(12.0, 75.0, base.plusMinutes(60)),
                reading(10.0, 75.0, base.plusMinutes(30)),
                reading(4.0, 75.0, base)
        );

        RiskAssessmentResult result = riskAssessmentService.assessRisk(descendingReadings, Collections.emptyList(), base, base.plusHours(2));

        assertThat(result.sufficientData()).isTrue();
        assertThat(result.drivers().accumulatedDegreeHours()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Irregular intervals with large gaps do not erroneously multiply continuous exposure")
    void assessRisk_LargeGap_DoesNotSpuriouslyInflateScore() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 11, 10, 0);
        // Reading 1 at 10:00, Reading 2 at 10:30, Reading 3 at 22:00 (12 hour gap, maxInterval is 2h)
        List<SensorReading> readings = List.of(
                reading(10.0, 75.0, base),
                reading(10.0, 75.0, base.plusMinutes(30)),
                reading(10.0, 75.0, base.plusHours(12))
        );

        RiskAssessmentResult result = riskAssessmentService.assessRisk(readings, Collections.emptyList(), base, base.plusHours(13));

        assertThat(result.sufficientData()).isTrue();
        // Since interval was capped at maxIntervalMinutes (120m), accumulatedDegreeHours cannot exceed 10°C excursion * (0.5h + 2.0h)
        assertThat(result.drivers().accumulatedDegreeHours()).isLessThan(new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("Duplicate timestamps are deduplicated cleanly without divide-by-zero")
    void assessRisk_DuplicateTimestamps_HandledCleanly() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 11, 10, 0);
        List<SensorReading> readings = List.of(
                reading(4.0, 75.0, base),
                reading(4.0, 75.0, base), // duplicate timestamp
                reading(4.5, 78.0, base.plusMinutes(30)),
                reading(4.2, 76.0, base.plusMinutes(60))
        );

        RiskAssessmentResult result = riskAssessmentService.assessRisk(readings, Collections.emptyList(), base, base.plusHours(2));

        assertThat(result.sufficientData()).isTrue();
        assertThat(result.readingsCount()).isEqualTo(3); // 3 distinct readings
    }

    @Test
    @DisplayName("Insufficient readings produces explicit insufficientData result and never returns LOW risk")
    void assessRisk_InsufficientReadings_ProducesExplicitResult() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 11, 10, 0);
        // Only 2 readings when minRequired is 3
        List<SensorReading> readings = List.of(
                reading(15.0, 75.0, base),
                reading(15.0, 75.0, base.plusMinutes(30))
        );

        RiskAssessmentResult result = riskAssessmentService.assessRisk(readings, Collections.emptyList(), base, base.plusHours(2));

        assertThat(result.sufficientData()).isFalse();
        assertThat(result.overallRiskScore()).isNull();
        assertThat(result.riskTier()).isNull();
        assertThat(result.statusMessage()).contains("Insufficient telemetry data: 2 readings found; minimum required is 3");
    }

    @Test
    @DisplayName("Empty readings collection produces explicit insufficientData result")
    void assessRisk_EmptyReadings_ProducesExplicitResult() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 11, 10, 0);

        RiskAssessmentResult result = riskAssessmentService.assessRisk(Collections.emptyList(), Collections.emptyList(), base, base.plusHours(2));

        assertThat(result.sufficientData()).isFalse();
        assertThat(result.overallRiskScore()).isNull();
        assertThat(result.riskTier()).isNull();
    }

    @Test
    @DisplayName("Alert clustering prevents runaway stacking for continuous repeated alerts")
    void assessRisk_RepeatedAlertsClustering_AvoidsRunawayPenalties() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 11, 10, 0);
        List<SensorReading> readings = List.of(
                reading(4.0, 75.0, base),
                reading(4.0, 75.0, base.plusMinutes(30)),
                reading(4.0, 75.0, base.plusMinutes(60))
        );

        // 3 consecutive HIGH alerts for same alert type within 60 minutes
        Alert a1 = new Alert(testSensor, testFacility, AlertType.HIGH_TEMPERATURE, AlertSeverity.HIGH, "High temp", new BigDecimal("10"), new BigDecimal("8"));
        a1.setCreatedAt(base.plusMinutes(5));
        Alert a2 = new Alert(testSensor, testFacility, AlertType.HIGH_TEMPERATURE, AlertSeverity.HIGH, "High temp", new BigDecimal("11"), new BigDecimal("8"));
        a2.setCreatedAt(base.plusMinutes(15));
        Alert a3 = new Alert(testSensor, testFacility, AlertType.HIGH_TEMPERATURE, AlertSeverity.HIGH, "High temp", new BigDecimal("11"), new BigDecimal("8"));
        a3.setCreatedAt(base.plusMinutes(25));

        RiskAssessmentResult result = riskAssessmentService.assessRisk(readings, List.of(a1, a2, a3), base, base.plusHours(2));

        assertThat(result.sufficientData()).isTrue();
        // Since alerts cluster into 1 incident, penalty is 35 (HIGH penalty), not 3 * 35 = 105
        assertThat(result.drivers().alertPenaltyScore()).isEqualByComparingTo(new BigDecimal("35.00"));
        assertThat(result.drivers().suppressedDuplicateIncidentsCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Risk tier cutoffs map correctly at boundaries")
    void assessRisk_TierCutoffBoundaries() {
        // Moderate threshold is 25.00, elevated is 55.00, critical is 80.00
        assertThat(riskProperties.getModerateThreshold()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(riskProperties.getElevatedThreshold()).isEqualByComparingTo(new BigDecimal("55.00"));
        assertThat(riskProperties.getCriticalThreshold()).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    @Test
    @DisplayName("Invalid configuration validation throws IllegalStateException")
    void validateProperties_InvalidWeights_ThrowsException() {
        RiskAssessmentProperties badProps = new RiskAssessmentProperties();
        badProps.setWeightThermalExcursions(new BigDecimal("0.50"));
        badProps.setWeightHumidityExcursions(new BigDecimal("0.50"));
        badProps.setWeightExcursionDuration(new BigDecimal("0.50")); // sum = 1.70

        assertThatThrownBy(badProps::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Risk assessment weights must sum approximately to 1.0");
    }

    @Test
    @DisplayName("assessFacilityRisk aggregates across multiple sensors and highlights hotspots and insufficient-data sensors")
    void assessFacilityRisk_MultiSensorAggregation_HighlightsHotspotsAndInsufficientData() {
        SensorRepository mockSensorRepo = org.mockito.Mockito.mock(SensorRepository.class);
        StorageFacilityRepository mockFacilityRepo = org.mockito.Mockito.mock(StorageFacilityRepository.class);
        SensorReadingRepository mockReadingRepo = org.mockito.Mockito.mock(SensorReadingRepository.class);
        AlertRepository mockAlertRepo = org.mockito.Mockito.mock(AlertRepository.class);

        RiskAssessmentService serviceWithMocks = new RiskAssessmentService(
                riskProperties,
                thresholdProperties,
                mockSensorRepo,
                mockFacilityRepo,
                mockReadingRepo,
                mockAlertRepo
        );

        Sensor sensor1 = new Sensor("SN-1", "Sensor 1", SensorType.TEMPERATURE_HUMIDITY, testFacility);
        sensor1.setId(101L);
        Sensor sensor2 = new Sensor("SN-2", "Sensor 2", SensorType.TEMPERATURE_HUMIDITY, testFacility);
        sensor2.setId(102L);

        org.mockito.Mockito.when(mockFacilityRepo.findById(1L)).thenReturn(java.util.Optional.of(testFacility));
        org.mockito.Mockito.when(mockSensorRepo.findByFacilityIdAndActive(1L, true)).thenReturn(List.of(sensor1, sensor2));

        LocalDateTime now = LocalDateTime.now();
        // Sensor 1 has high excursion readings
        List<SensorReading> readings1 = List.of(
                new SensorReading(sensor1, new BigDecimal("14.0"), new BigDecimal("75.0"), now.minusHours(3), ReadingDataSource.DEVICE),
                new SensorReading(sensor1, new BigDecimal("14.0"), new BigDecimal("75.0"), now.minusHours(2), ReadingDataSource.DEVICE),
                new SensorReading(sensor1, new BigDecimal("14.0"), new BigDecimal("75.0"), now.minusHours(1), ReadingDataSource.DEVICE)
        );
        // Sensor 2 has only 1 reading (insufficient data)
        List<SensorReading> readings2 = List.of(
                new SensorReading(sensor2, new BigDecimal("4.0"), new BigDecimal("75.0"), now.minusHours(1), ReadingDataSource.DEVICE)
        );

        org.mockito.Mockito.when(mockReadingRepo.findBySensorIdAndRecordedAtBetweenOrderByRecordedAtDesc(org.mockito.ArgumentMatchers.eq(101L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(readings1);
        org.mockito.Mockito.when(mockReadingRepo.findBySensorIdAndRecordedAtBetweenOrderByRecordedAtDesc(org.mockito.ArgumentMatchers.eq(102L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(readings2);

        com.coldchainsentinel.dto.FacilityRiskAssessmentResponse response = serviceWithMocks.assessFacilityRisk(1L, 24);

        assertThat(response.totalActiveSensors()).isEqualTo(2);
        assertThat(response.sensorsWithSufficientData()).isEqualTo(1);
        assertThat(response.sensorsWithInsufficientData()).isEqualTo(1);
        assertThat(response.facilityRiskScore()).isNotNull();
        assertThat(response.facilityRiskScore()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.facilityRecommendations()).anyMatch(r -> r.contains("insufficient telemetry"));
    }

    @Test
    @DisplayName("assessSensorRisk throws ResourceNotFoundException when sensor missing")
    void assessSensorRisk_NotFound_ThrowsException() {
        SensorRepository mockSensorRepo = org.mockito.Mockito.mock(SensorRepository.class);
        RiskAssessmentService serviceWithMocks = new RiskAssessmentService(
                riskProperties, thresholdProperties, mockSensorRepo, null, null, null
        );

        org.mockito.Mockito.when(mockSensorRepo.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> serviceWithMocks.assessSensorRisk(999L, 24))
                .isInstanceOf(com.coldchainsentinel.exception.ResourceNotFoundException.class)
                .hasMessageContaining("Sensor not found with id: 999");
    }

    @Test
    @DisplayName("assessFacilityRisk rejects invalid window hours with IllegalArgumentException")
    void assessSensorRisk_InvalidWindow_ThrowsException() {
        assertThatThrownBy(() -> riskAssessmentService.assessSensorRisk(10L, -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windowHours must be a positive integer");

        assertThatThrownBy(() -> riskAssessmentService.assessSensorRisk(10L, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windowHours cannot exceed 720");
    }

    @Test
    @DisplayName("LOW-tier facility with resolved critical alert never returns blanket stable statement")
    void assessFacilityRisk_LowTierWithCriticalHistoricalAlert_DoesNotReturnBlanketStableStatement() {
        SensorRepository mockSensorRepo = org.mockito.Mockito.mock(SensorRepository.class);
        StorageFacilityRepository mockFacilityRepo = org.mockito.Mockito.mock(StorageFacilityRepository.class);
        SensorReadingRepository mockReadingRepo = org.mockito.Mockito.mock(SensorReadingRepository.class);
        AlertRepository mockAlertRepo = org.mockito.Mockito.mock(AlertRepository.class);

        RiskAssessmentService serviceWithMocks = new RiskAssessmentService(
                riskProperties, thresholdProperties, mockSensorRepo, mockFacilityRepo, mockReadingRepo, mockAlertRepo
        );

        Sensor sensor = new Sensor("SN-LOW-01", "Zone A Low", SensorType.TEMPERATURE_HUMIDITY, testFacility);
        sensor.setId(201L);

        org.mockito.Mockito.when(mockFacilityRepo.findById(1L)).thenReturn(java.util.Optional.of(testFacility));
        org.mockito.Mockito.when(mockSensorRepo.findByFacilityIdAndActive(1L, true)).thenReturn(List.of(sensor));

        LocalDateTime now = LocalDateTime.now();
        // Readings are strictly normal (4.5 °C, safe range 0.00 - 8.00 °C)
        List<SensorReading> readings = List.of(
                new SensorReading(sensor, new BigDecimal("4.50"), new BigDecimal("75.0"), now.minusMinutes(10), ReadingDataSource.DEVICE),
                new SensorReading(sensor, new BigDecimal("4.50"), new BigDecimal("75.0"), now.minusMinutes(30), ReadingDataSource.DEVICE),
                new SensorReading(sensor, new BigDecimal("4.50"), new BigDecimal("75.0"), now.minusMinutes(60), ReadingDataSource.DEVICE)
        );

        // One historical critical alert that is RESOLVED
        Alert historicalAlert = new Alert(
                sensor, testFacility, AlertType.HIGH_TEMPERATURE, AlertSeverity.CRITICAL,
                "Critical temperature spike earlier", new BigDecimal("14.50"), new BigDecimal("8.00")
        );
        historicalAlert.setStatus(AlertStatus.RESOLVED);

        org.mockito.Mockito.when(mockReadingRepo.findBySensorIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                org.mockito.ArgumentMatchers.eq(201L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(readings);

        org.mockito.Mockito.when(mockAlertRepo.findBySensorIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(201L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of(historicalAlert));

        com.coldchainsentinel.dto.FacilityRiskAssessmentResponse response = serviceWithMocks.assessFacilityRisk(1L, 24);

        assertThat(response.facilityRiskTier()).isEqualTo(RiskTier.LOW);
        assertThat(response.facilityRecommendations())
                .noneMatch(r -> r.contains("All monitored zones are stable within normal safe operational boundaries"));
        assertThat(response.facilityRecommendations())
                .anyMatch(r -> r.contains("Historical alert status") && r.contains("1 critical/high alert(s)"));
    }

    @Test
    @DisplayName("Sensor with historical excursion but returned telemetry clearly distinguishes historical from active condition")
    void assessFacilityRisk_ExcursionWithReturnedTelemetry_DistinguishesHistoricalFromActive() {
        SensorRepository mockSensorRepo = org.mockito.Mockito.mock(SensorRepository.class);
        StorageFacilityRepository mockFacilityRepo = org.mockito.Mockito.mock(StorageFacilityRepository.class);
        SensorReadingRepository mockReadingRepo = org.mockito.Mockito.mock(SensorReadingRepository.class);
        AlertRepository mockAlertRepo = org.mockito.Mockito.mock(AlertRepository.class);

        RiskAssessmentService serviceWithMocks = new RiskAssessmentService(
                riskProperties, thresholdProperties, mockSensorRepo, mockFacilityRepo, mockReadingRepo, mockAlertRepo
        );

        Sensor sensor = new Sensor("SN-HIST-01", "Zone B Peaked", SensorType.TEMPERATURE_HUMIDITY, testFacility);
        sensor.setId(202L);

        org.mockito.Mockito.when(mockFacilityRepo.findById(1L)).thenReturn(java.util.Optional.of(testFacility));
        org.mockito.Mockito.when(mockSensorRepo.findByFacilityIdAndActive(1L, true)).thenReturn(List.of(sensor));

        LocalDateTime now = LocalDateTime.now();
        // Readings ordered DESC: latest is index 0 at 4.50 °C (SAFE), earlier peaked at 14.50 °C (> 8.00 °C)
        List<SensorReading> readingsDesc = List.of(
                new SensorReading(sensor, new BigDecimal("4.50"), new BigDecimal("75.0"), now.minusMinutes(10), ReadingDataSource.DEVICE),
                new SensorReading(sensor, new BigDecimal("12.00"), new BigDecimal("75.0"), now.minusMinutes(40), ReadingDataSource.DEVICE),
                new SensorReading(sensor, new BigDecimal("14.50"), new BigDecimal("75.0"), now.minusMinutes(70), ReadingDataSource.DEVICE)
        );

        org.mockito.Mockito.when(mockReadingRepo.findBySensorIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                org.mockito.ArgumentMatchers.eq(202L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(readingsDesc);

        org.mockito.Mockito.when(mockAlertRepo.findBySensorIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(202L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(Collections.emptyList());

        com.coldchainsentinel.dto.FacilityRiskAssessmentResponse response = serviceWithMocks.assessFacilityRisk(1L, 24);

        // Never return blanket stable statement when excursion occurred
        assertThat(response.facilityRecommendations())
                .noneMatch(r -> r.contains("All monitored zones are stable within normal safe operational boundaries"));

        // Historical excursion is explicitly detected and reports safe return
        assertThat(response.facilityRecommendations())
                .anyMatch(r -> r.contains("Historical excursion detected")
                        && r.contains("peaked at 14.50 °C")
                        && r.contains("returned within safe operational limits"));

        // Must NOT falsely claim conditions are currently active breach
        assertThat(response.facilityRecommendations())
                .noneMatch(r -> r.contains("Active temperature breach"));
    }

    @Test
    @DisplayName("Sensor with currently active excursion reports active breach condition")
    void assessFacilityRisk_ActiveExcursion_ReportsActiveBreachCondition() {
        SensorRepository mockSensorRepo = org.mockito.Mockito.mock(SensorRepository.class);
        StorageFacilityRepository mockFacilityRepo = org.mockito.Mockito.mock(StorageFacilityRepository.class);
        SensorReadingRepository mockReadingRepo = org.mockito.Mockito.mock(SensorReadingRepository.class);
        AlertRepository mockAlertRepo = org.mockito.Mockito.mock(AlertRepository.class);

        RiskAssessmentService serviceWithMocks = new RiskAssessmentService(
                riskProperties, thresholdProperties, mockSensorRepo, mockFacilityRepo, mockReadingRepo, mockAlertRepo
        );

        Sensor sensor = new Sensor("SN-ACT-01", "Zone C Active", SensorType.TEMPERATURE_HUMIDITY, testFacility);
        sensor.setId(203L);

        org.mockito.Mockito.when(mockFacilityRepo.findById(1L)).thenReturn(java.util.Optional.of(testFacility));
        org.mockito.Mockito.when(mockSensorRepo.findByFacilityIdAndActive(1L, true)).thenReturn(List.of(sensor));

        LocalDateTime now = LocalDateTime.now();
        // Readings ordered DESC: latest is index 0 at 14.50 °C (ACTIVE BREACH)
        List<SensorReading> readingsDesc = List.of(
                new SensorReading(sensor, new BigDecimal("14.50"), new BigDecimal("75.0"), now.minusMinutes(5), ReadingDataSource.DEVICE),
                new SensorReading(sensor, new BigDecimal("12.00"), new BigDecimal("75.0"), now.minusMinutes(35), ReadingDataSource.DEVICE),
                new SensorReading(sensor, new BigDecimal("9.00"), new BigDecimal("75.0"), now.minusMinutes(65), ReadingDataSource.DEVICE)
        );

        org.mockito.Mockito.when(mockReadingRepo.findBySensorIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                org.mockito.ArgumentMatchers.eq(203L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(readingsDesc);

        org.mockito.Mockito.when(mockAlertRepo.findBySensorIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(203L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(Collections.emptyList());

        com.coldchainsentinel.dto.FacilityRiskAssessmentResponse response = serviceWithMocks.assessFacilityRisk(1L, 24);

        assertThat(response.facilityRecommendations())
                .anyMatch(r -> r.contains("Active temperature breach")
                        && r.contains("currently reporting 14.50 °C")
                        && r.contains("exceeding safe maximum of 8.00 °C"));
        assertThat(response.facilityRecommendations())
                .noneMatch(r -> r.contains("All monitored zones are stable within normal safe operational boundaries"));
    }

    @Test
    @DisplayName("Completely stable sensor facility returns all zones stable statement and empty hotspots")
    void assessFacilityRisk_CompletelyStableSensor_ReturnsAllZonesStableStatement() {
        SensorRepository mockSensorRepo = org.mockito.Mockito.mock(SensorRepository.class);
        StorageFacilityRepository mockFacilityRepo = org.mockito.Mockito.mock(StorageFacilityRepository.class);
        SensorReadingRepository mockReadingRepo = org.mockito.Mockito.mock(SensorReadingRepository.class);
        AlertRepository mockAlertRepo = org.mockito.Mockito.mock(AlertRepository.class);

        RiskAssessmentService serviceWithMocks = new RiskAssessmentService(
                riskProperties, thresholdProperties, mockSensorRepo, mockFacilityRepo, mockReadingRepo, mockAlertRepo
        );

        Sensor sensor = new Sensor("SN-OK-01", "Zone D Safe", SensorType.TEMPERATURE_HUMIDITY, testFacility);
        sensor.setId(204L);

        org.mockito.Mockito.when(mockFacilityRepo.findById(1L)).thenReturn(java.util.Optional.of(testFacility));
        org.mockito.Mockito.when(mockSensorRepo.findByFacilityIdAndActive(1L, true)).thenReturn(List.of(sensor));

        LocalDateTime now = LocalDateTime.now();
        List<SensorReading> readingsDesc = List.of(
                new SensorReading(sensor, new BigDecimal("4.50"), new BigDecimal("75.0"), now.minusMinutes(10), ReadingDataSource.DEVICE),
                new SensorReading(sensor, new BigDecimal("4.20"), new BigDecimal("75.0"), now.minusMinutes(30), ReadingDataSource.DEVICE),
                new SensorReading(sensor, new BigDecimal("4.00"), new BigDecimal("75.0"), now.minusMinutes(50), ReadingDataSource.DEVICE)
        );

        org.mockito.Mockito.when(mockReadingRepo.findBySensorIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                org.mockito.ArgumentMatchers.eq(204L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(readingsDesc);

        org.mockito.Mockito.when(mockAlertRepo.findBySensorIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(204L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(Collections.emptyList());

        com.coldchainsentinel.dto.FacilityRiskAssessmentResponse response = serviceWithMocks.assessFacilityRisk(1L, 24);

        assertThat(response.facilityRiskTier()).isEqualTo(RiskTier.LOW);
        assertThat(response.facilityRiskScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.facilityRecommendations())
                .contains("All monitored zones are stable within normal safe operational boundaries.");
        assertThat(response.hotspotSensors()).isEmpty();
    }

    @Test
    @DisplayName("Facility with insufficient-data sensors surfaces them in hotspots and recommendations")
    void assessFacilityRisk_InsufficientDataSensors_SurfacedInHotspotsAndRecommendations() {
        SensorRepository mockSensorRepo = org.mockito.Mockito.mock(SensorRepository.class);
        StorageFacilityRepository mockFacilityRepo = org.mockito.Mockito.mock(StorageFacilityRepository.class);
        SensorReadingRepository mockReadingRepo = org.mockito.Mockito.mock(SensorReadingRepository.class);
        AlertRepository mockAlertRepo = org.mockito.Mockito.mock(AlertRepository.class);

        RiskAssessmentService serviceWithMocks = new RiskAssessmentService(
                riskProperties, thresholdProperties, mockSensorRepo, mockFacilityRepo, mockReadingRepo, mockAlertRepo
        );

        Sensor sensorGood = new Sensor("SN-GOOD-01", "Zone Normal", SensorType.TEMPERATURE_HUMIDITY, testFacility);
        sensorGood.setId(205L);
        Sensor sensorSparse = new Sensor("SN-SPARSE-02", "Zone Sparse", SensorType.TEMPERATURE_HUMIDITY, testFacility);
        sensorSparse.setId(206L);

        org.mockito.Mockito.when(mockFacilityRepo.findById(1L)).thenReturn(java.util.Optional.of(testFacility));
        org.mockito.Mockito.when(mockSensorRepo.findByFacilityIdAndActive(1L, true)).thenReturn(List.of(sensorGood, sensorSparse));

        LocalDateTime now = LocalDateTime.now();
        List<SensorReading> goodReadings = List.of(
                new SensorReading(sensorGood, new BigDecimal("4.50"), new BigDecimal("75.0"), now.minusMinutes(10), ReadingDataSource.DEVICE),
                new SensorReading(sensorGood, new BigDecimal("4.20"), new BigDecimal("75.0"), now.minusMinutes(30), ReadingDataSource.DEVICE),
                new SensorReading(sensorGood, new BigDecimal("4.00"), new BigDecimal("75.0"), now.minusMinutes(50), ReadingDataSource.DEVICE)
        );
        // Sparse sensor has only 1 reading (< 3 minReadingsRequired)
        List<SensorReading> sparseReadings = List.of(
                new SensorReading(sensorSparse, new BigDecimal("4.50"), new BigDecimal("75.0"), now.minusMinutes(10), ReadingDataSource.DEVICE)
        );

        org.mockito.Mockito.when(mockReadingRepo.findBySensorIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                org.mockito.ArgumentMatchers.eq(205L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(goodReadings);

        org.mockito.Mockito.when(mockReadingRepo.findBySensorIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                org.mockito.ArgumentMatchers.eq(206L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(sparseReadings);

        org.mockito.Mockito.when(mockAlertRepo.findBySensorIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(Collections.emptyList());

        com.coldchainsentinel.dto.FacilityRiskAssessmentResponse response = serviceWithMocks.assessFacilityRisk(1L, 24);

        assertThat(response.totalActiveSensors()).isEqualTo(2);
        assertThat(response.sensorsWithSufficientData()).isEqualTo(1);
        assertThat(response.sensorsWithInsufficientData()).isEqualTo(1);

        // Insufficient-data sensor must be surfaced in hotspots rather than silently omitted
        assertThat(response.hotspotSensors())
                .anyMatch(s -> s.sensorCode().equals("SN-SPARSE-02") && !s.sufficientData() && s.riskScore() == null);

        // Recommendations must surface the insufficient data and never state all zones are stable
        assertThat(response.facilityRecommendations())
                .anyMatch(r -> r.contains("1 of 2 active sensors have insufficient telemetry"));
        assertThat(response.facilityRecommendations())
                .noneMatch(r -> r.contains("All monitored zones are stable within normal safe operational boundaries"));
    }

    @Test
    @DisplayName("Resolved alerts continue contributing to historical exposure risk while recommendations identify them as resolved")
    void assessRisk_ResolvedAlerts_ContributeToRiskPenaltyAndMarkedResolvedInRecommendations() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 11, 10, 0);
        // Normal readings so baseline sensor score without alerts is 0.00
        List<SensorReading> readings = List.of(
                reading(4.0, 75.0, base),
                reading(4.5, 76.0, base.plusMinutes(30)),
                reading(4.2, 75.0, base.plusMinutes(60))
        );

        Alert resolvedCriticalAlert = new Alert(
                testSensor, testFacility, AlertType.HIGH_TEMPERATURE,
                AlertSeverity.CRITICAL, "Historical critical temperature excursion",
                new BigDecimal("14.50"), new BigDecimal("8.00")
        );
        resolvedCriticalAlert.setCreatedAt(base.plusMinutes(15));
        resolvedCriticalAlert.setStatus(AlertStatus.RESOLVED);

        RiskAssessmentResult result = riskAssessmentService.assessRisk(
                readings, List.of(resolvedCriticalAlert), base, base.plusHours(2)
        );

        assertThat(result.sufficientData()).isTrue();
        // Critical alert penalty is 50.00, weight is 0.20 -> 10.00 contribution
        assertThat(result.drivers().alertPenaltyScore()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(result.overallRiskScore()).isEqualByComparingTo(new BigDecimal("10.00"));

        // Recommendations accurately state alerts are resolved and do not demand active technician dispatch
        assertThat(result.recommendations())
                .anyMatch(r -> r.contains("Historical alerts evaluated: 1 critical/high alert(s) occurred during this observation window and have been marked resolved."));
        assertThat(result.recommendations())
                .noneMatch(r -> r.contains("prioritize technician dispatch"));
        assertThat(result.recommendations())
                .noneMatch(r -> r.contains("Active alert status"));
    }

    @Test
    @DisplayName("Telemetry gap greater than 120 minutes is capped correctly and does not accumulate unbounded exposure")
    void assessRisk_TelemetryGapGreaterThan120Minutes_IsCappedAtMaxInterval() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 11, 10, 0);
        // Excursion of 6.0 °C (14.0 °C vs max 8.0 °C)
        // Reading 1 at 10:00, Reading 2 at 20:00 (600 min gap), Reading 3 at 20:30 (30 min)
        List<SensorReading> readings = List.of(
                reading(14.0, 75.0, base),
                reading(14.0, 75.0, base.plusMinutes(600)),
                reading(14.0, 75.0, base.plusMinutes(630))
        );

        RiskAssessmentResult result = riskAssessmentService.assessRisk(readings, Collections.emptyList(), base, base.plusHours(12));

        assertThat(result.sufficientData()).isTrue();
        // With capping at 120 minutes (2.0 hours) instead of 600 minutes (10.0 hours):
        // Interval 1: 6.0 °C * 2.0 h = 12.00 degree-hours (instead of 60.00)
        // Interval 2: 6.0 °C * 0.5 h = 3.00 degree-hours
        // Total degree-hours must be exactly 15.00
        assertThat(result.drivers().accumulatedDegreeHours()).isEqualByComparingTo(new BigDecimal("15.00"));
        // Total excursion duration must be capped to 120 + 30 = 150 minutes (not 630 minutes)
        assertThat(result.drivers().totalExcursionMinutes()).isEqualTo(150L);
        assertThat(result.drivers().maxContinuousExcursionMinutes()).isEqualTo(150L);
    }

    @Test
    @DisplayName("Inactive facility receives explicit rejection with IllegalArgumentException")
    void assessFacilityRisk_InactiveFacility_ThrowsIllegalArgumentException() {
        StorageFacility inactiveFacility = new StorageFacility("Decommissioned Hub", "Zone 9", new BigDecimal("100"), FacilityType.COLD_STORAGE);
        inactiveFacility.setId(99L);
        inactiveFacility.setActive(false);

        StorageFacilityRepository mockFacilityRepo = org.mockito.Mockito.mock(StorageFacilityRepository.class);
        SensorRepository mockSensorRepo = org.mockito.Mockito.mock(SensorRepository.class);
        SensorReadingRepository mockReadingRepo = org.mockito.Mockito.mock(SensorReadingRepository.class);
        AlertRepository mockAlertRepo = org.mockito.Mockito.mock(AlertRepository.class);

        org.mockito.Mockito.when(mockFacilityRepo.findById(99L)).thenReturn(java.util.Optional.of(inactiveFacility));

        RiskAssessmentService serviceWithMocks = new RiskAssessmentService(
                riskProperties, thresholdProperties, mockSensorRepo, mockFacilityRepo, mockReadingRepo, mockAlertRepo
        );

        assertThatThrownBy(() -> serviceWithMocks.assessFacilityRisk(99L, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Storage facility is inactive and cannot be assessed for operational risk. Facility ID: 99");

        // Verify sensors are never queried for inactive facility
        org.mockito.Mockito.verifyNoInteractions(mockSensorRepo);
    }
}
