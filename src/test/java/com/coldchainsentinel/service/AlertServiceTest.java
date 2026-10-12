package com.coldchainsentinel.service;

import com.coldchainsentinel.config.AlertThresholdProperties;
import com.coldchainsentinel.dto.AlertResponse;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.*;
import com.coldchainsentinel.repository.AlertRepository;
import com.coldchainsentinel.repository.SensorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private SensorRepository sensorRepository;

    private AlertThresholdProperties thresholds;
    private AlertService alertService;

    private StorageFacility facility;
    private Sensor sensor;

    @BeforeEach
    void setUp() {
        thresholds = new AlertThresholdProperties();
        thresholds.setTemperatureMin(new BigDecimal("0.00"));
        thresholds.setTemperatureMax(new BigDecimal("8.00"));
        thresholds.setTemperatureCriticalDelta(new BigDecimal("4.00"));
        thresholds.setHumidityMin(new BigDecimal("65.00"));
        thresholds.setHumidityMax(new BigDecimal("95.00"));
        thresholds.setHumidityCriticalDelta(new BigDecimal("10.00"));

        alertService = new AlertService(alertRepository, sensorRepository, thresholds);

        facility = new StorageFacility("Facility 1", "Location 1", new BigDecimal("100"), FacilityType.COLD_STORAGE);
        facility.setId(10L);

        sensor = new Sensor("SN-001", "Sensor 1", SensorType.TEMPERATURE_HUMIDITY, facility);
        sensor.setId(100L);
    }

    @Test
    @DisplayName("Normal readings within safe thresholds do not generate alerts")
    void evaluateReading_NormalReadings_GeneratesNoAlerts() {
        SensorReading reading = new SensorReading(sensor, new BigDecimal("4.50"), new BigDecimal("80.00"), LocalDateTime.now(), ReadingDataSource.DEVICE);

        List<AlertResponse> alerts = alertService.evaluateReading(reading);

        assertThat(alerts).isEmpty();
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("High temperature violation creates an alert with calculated severity")
    void evaluateReading_HighTemperature_CreatesAlert() {
        // Threshold max is 8.00, critical delta is 4.00 (half is 2.00)
        // 13.00 exceeds by 5.00 >= 4.00 -> CRITICAL
        SensorReading reading = new SensorReading(sensor, new BigDecimal("13.00"), new BigDecimal("80.00"), LocalDateTime.now(), ReadingDataSource.DEVICE);

        when(alertRepository.existsBySensorIdAndAlertTypeAndStatus(100L, AlertType.HIGH_TEMPERATURE, AlertStatus.OPEN))
                .thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert a = invocation.getArgument(0);
            a.setId(1L);
            return a;
        });

        List<AlertResponse> alerts = alertService.evaluateReading(reading);

        assertThat(alerts).hasSize(1);
        AlertResponse alert = alerts.get(0);
        assertThat(alert.alertType()).isEqualTo(AlertType.HIGH_TEMPERATURE);
        assertThat(alert.severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(alert.status()).isEqualTo(AlertStatus.OPEN);
        assertThat(alert.measuredValue()).isEqualByComparingTo("13.00");
        assertThat(alert.thresholdValue()).isEqualByComparingTo("8.00");
        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    @DisplayName("Low temperature violation creates LOW_TEMPERATURE alert")
    void evaluateReading_LowTemperature_CreatesAlert() {
        // Min is 0.00, reading is -1.00 (diff 1.00 < half delta 2.00) -> MEDIUM severity
        SensorReading reading = new SensorReading(sensor, new BigDecimal("-1.00"), new BigDecimal("75.00"), LocalDateTime.now(), ReadingDataSource.DEVICE);

        when(alertRepository.existsBySensorIdAndAlertTypeAndStatus(100L, AlertType.LOW_TEMPERATURE, AlertStatus.OPEN))
                .thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert a = invocation.getArgument(0);
            a.setId(2L);
            return a;
        });

        List<AlertResponse> alerts = alertService.evaluateReading(reading);

        assertThat(alerts).hasSize(1);
        AlertResponse alert = alerts.get(0);
        assertThat(alert.alertType()).isEqualTo(AlertType.LOW_TEMPERATURE);
        assertThat(alert.severity()).isEqualTo(AlertSeverity.MEDIUM);
        assertThat(alert.status()).isEqualTo(AlertStatus.OPEN);
        assertThat(alert.measuredValue()).isEqualByComparingTo("-1.00");
    }

    @Test
    @DisplayName("High humidity violation creates HIGH_HUMIDITY alert")
    void evaluateReading_HighHumidity_CreatesAlert() {
        // Max is 95.00, reading is 98.00 -> delta 3.00 < half delta 5.00 -> MEDIUM
        SensorReading reading = new SensorReading(sensor, new BigDecimal("4.00"), new BigDecimal("98.00"), LocalDateTime.now(), ReadingDataSource.DEVICE);

        when(alertRepository.existsBySensorIdAndAlertTypeAndStatus(100L, AlertType.HIGH_HUMIDITY, AlertStatus.OPEN))
                .thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert a = invocation.getArgument(0);
            a.setId(3L);
            return a;
        });

        List<AlertResponse> alerts = alertService.evaluateReading(reading);

        assertThat(alerts).hasSize(1);
        AlertResponse alert = alerts.get(0);
        assertThat(alert.alertType()).isEqualTo(AlertType.HIGH_HUMIDITY);
        assertThat(alert.severity()).isEqualTo(AlertSeverity.MEDIUM);
    }

    @Test
    @DisplayName("Low humidity violation creates LOW_HUMIDITY alert")
    void evaluateReading_LowHumidity_CreatesAlert() {
        // Min is 65.00, reading is 50.00 -> diff 15.00 >= critical delta 10.00 -> CRITICAL
        SensorReading reading = new SensorReading(sensor, new BigDecimal("4.00"), new BigDecimal("50.00"), LocalDateTime.now(), ReadingDataSource.DEVICE);

        when(alertRepository.existsBySensorIdAndAlertTypeAndStatus(100L, AlertType.LOW_HUMIDITY, AlertStatus.OPEN))
                .thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert a = invocation.getArgument(0);
            a.setId(4L);
            return a;
        });

        List<AlertResponse> alerts = alertService.evaluateReading(reading);

        assertThat(alerts).hasSize(1);
        AlertResponse alert = alerts.get(0);
        assertThat(alert.alertType()).isEqualTo(AlertType.LOW_HUMIDITY);
        assertThat(alert.severity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    @DisplayName("Observed reading 12.80C and 4.60% triggers both HIGH_TEMPERATURE and LOW_HUMIDITY alerts")
    void evaluateReading_ObservedExcursion_TriggersExpectedAlerts() {
        // 12.80C exceeds 8.00C (diff 4.80 >= 4.00 critical delta -> CRITICAL HIGH_TEMPERATURE)
        // 4.60% humidity is well below 65.00% (diff 60.40 >= 10.00 critical delta -> CRITICAL LOW_HUMIDITY)
        SensorReading reading = new SensorReading(
                sensor,
                new BigDecimal("12.80"),
                new BigDecimal("4.60"),
                LocalDateTime.now(),
                ReadingDataSource.SIMULATED
        );

        when(alertRepository.existsBySensorIdAndAlertTypeAndStatus(100L, AlertType.HIGH_TEMPERATURE, AlertStatus.OPEN))
                .thenReturn(false);
        when(alertRepository.existsBySensorIdAndAlertTypeAndStatus(100L, AlertType.LOW_HUMIDITY, AlertStatus.OPEN))
                .thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert a = invocation.getArgument(0);
            a.setId(99L);
            return a;
        });

        List<AlertResponse> alerts = alertService.evaluateReading(reading);

        assertThat(alerts).hasSize(2);
        assertThat(alerts).extracting(AlertResponse::alertType)
                .containsExactlyInAnyOrder(AlertType.HIGH_TEMPERATURE, AlertType.LOW_HUMIDITY);
        assertThat(alerts).allMatch(a -> a.severity() == AlertSeverity.CRITICAL);
    }

    @Test
    @DisplayName("Thermal excursion reading with normal humidity (12.80C and 76.00%) only triggers HIGH_TEMPERATURE alert")
    void evaluateReading_FixedThermalExcursion_TriggersOnlyTemperatureAlert() {
        SensorReading reading = new SensorReading(
                sensor,
                new BigDecimal("12.80"),
                new BigDecimal("76.00"),
                LocalDateTime.now(),
                ReadingDataSource.SIMULATED
        );

        when(alertRepository.existsBySensorIdAndAlertTypeAndStatus(100L, AlertType.HIGH_TEMPERATURE, AlertStatus.OPEN))
                .thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert a = invocation.getArgument(0);
            a.setId(100L);
            return a;
        });

        List<AlertResponse> alerts = alertService.evaluateReading(reading);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).alertType()).isEqualTo(AlertType.HIGH_TEMPERATURE);
        assertThat(alerts.get(0).severity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    @DisplayName("Duplicate OPEN alert suppression: does not create alert if OPEN alert already exists for same sensor and alert type")
    void evaluateReading_DuplicateOpenAlert_IsSuppressed() {
        SensorReading reading = new SensorReading(sensor, new BigDecimal("12.00"), new BigDecimal("80.00"), LocalDateTime.now(), ReadingDataSource.DEVICE);

        when(alertRepository.existsBySensorIdAndAlertTypeAndStatus(100L, AlertType.HIGH_TEMPERATURE, AlertStatus.OPEN))
                .thenReturn(true);

        List<AlertResponse> alerts = alertService.evaluateReading(reading);

        assertThat(alerts).isEmpty();
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("New alert is allowed after previous alert of that type has been resolved")
    void evaluateReading_AfterResolution_AllowsNewAlert() {
        SensorReading reading = new SensorReading(sensor, new BigDecimal("12.00"), new BigDecimal("80.00"), LocalDateTime.now(), ReadingDataSource.DEVICE);

        // Previous alert was RESOLVED, so existsBy... with OPEN returns false
        when(alertRepository.existsBySensorIdAndAlertTypeAndStatus(100L, AlertType.HIGH_TEMPERATURE, AlertStatus.OPEN))
                .thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert a = invocation.getArgument(0);
            a.setId(5L);
            return a;
        });

        List<AlertResponse> alerts = alertService.evaluateReading(reading);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).status()).isEqualTo(AlertStatus.OPEN);
        verify(alertRepository, times(1)).save(any(Alert.class));
    }

    @Test
    @DisplayName("Acknowledge alert transitions OPEN to ACKNOWLEDGED with timestamp")
    void acknowledgeAlert_Success() {
        Alert alert = new Alert(sensor, facility, AlertType.HIGH_TEMPERATURE, AlertSeverity.HIGH, "High temp", new BigDecimal("10.00"), new BigDecimal("8.00"));
        alert.setId(10L);
        alert.setStatus(AlertStatus.OPEN);

        when(alertRepository.findById(10L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertResponse response = alertService.acknowledgeAlert(10L);

        assertThat(response.status()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(response.acknowledgedAt()).isNotNull();
    }

    @Test
    @DisplayName("Acknowledge alert fails if alert is not in OPEN status")
    void acknowledgeAlert_InvalidStatus_ThrowsException() {
        Alert alert = new Alert(sensor, facility, AlertType.HIGH_TEMPERATURE, AlertSeverity.HIGH, "High temp", new BigDecimal("10.00"), new BigDecimal("8.00"));
        alert.setId(10L);
        alert.setStatus(AlertStatus.ACKNOWLEDGED);

        when(alertRepository.findById(10L)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> alertService.acknowledgeAlert(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only OPEN alerts can be acknowledged");
    }

    @Test
    @DisplayName("Resolve alert transitions OPEN/ACKNOWLEDGED to RESOLVED with timestamp")
    void resolveAlert_Success() {
        Alert alert = new Alert(sensor, facility, AlertType.HIGH_TEMPERATURE, AlertSeverity.HIGH, "High temp", new BigDecimal("10.00"), new BigDecimal("8.00"));
        alert.setId(10L);
        alert.setStatus(AlertStatus.ACKNOWLEDGED);

        when(alertRepository.findById(10L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertResponse response = alertService.resolveAlert(10L);

        assertThat(response.status()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(response.resolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("Resolve alert rejects already RESOLVED alert")
    void resolveAlert_AlreadyResolved_ThrowsException() {
        Alert alert = new Alert(sensor, facility, AlertType.HIGH_TEMPERATURE, AlertSeverity.HIGH, "High temp", new BigDecimal("10.00"), new BigDecimal("8.00"));
        alert.setId(10L);
        alert.setStatus(AlertStatus.RESOLVED);

        when(alertRepository.findById(10L)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> alertService.resolveAlert(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Alert is already resolved");
    }

    @Test
    @DisplayName("Missing alert ID throws ResourceNotFoundException")
    void getAlertById_NotFound_ThrowsException() {
        when(alertRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.getAlertById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Alert not found with id: 999");
    }

    @Test
    @DisplayName("Missing sensor for alerts throws ResourceNotFoundException")
    void getAlertsBySensor_SensorNotFound_ThrowsException() {
        when(sensorRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> alertService.getAlertsBySensor(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Sensor not found with id: 999");
    }

    @Test
    @DisplayName("Paginated alerts with status and severity filters")
    void getAlerts_WithFilters_ReturnsPagedResponse() {
        Pageable pageable = PageRequest.of(0, 10);
        Alert alert = new Alert(sensor, facility, AlertType.HIGH_TEMPERATURE, AlertSeverity.CRITICAL, "Critical temp", new BigDecimal("14.00"), new BigDecimal("8.00"));
        alert.setId(1L);

        Page<Alert> page = new PageImpl<>(List.of(alert), pageable, 1);
        when(alertRepository.findByStatusAndSeverity(AlertStatus.OPEN, AlertSeverity.CRITICAL, pageable)).thenReturn(page);

        PagedResponse<AlertResponse> response = alertService.getAlerts(AlertStatus.OPEN, AlertSeverity.CRITICAL, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Readings evaluated against assigned active commodity profile limits")
    void evaluateReading_WithAssignedActiveCommodityProfile_UsesProfileLimits() {
        // Apples profile: 0.50 °C to 2.50 °C, 85.00 % to 95.00 %
        CommodityProfile applesProfile = new CommodityProfile(
                "Apples Standard",
                "Apples",
                "Fuji",
                new BigDecimal("0.50"),
                new BigDecimal("2.50"),
                new BigDecimal("85.00"),
                new BigDecimal("95.00")
        );
        applesProfile.setId(5L);
        applesProfile.setActive(true);
        facility.setCommodityProfile(applesProfile);

        // 3.50 °C is safe under global thresholds (0-8 °C), but breaches Apples max (2.50 °C)
        SensorReading reading = new SensorReading(sensor, new BigDecimal("3.50"), new BigDecimal("90.00"), LocalDateTime.now(), ReadingDataSource.DEVICE);

        when(alertRepository.existsBySensorIdAndAlertTypeAndStatus(100L, AlertType.HIGH_TEMPERATURE, AlertStatus.OPEN))
                .thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });

        List<AlertResponse> alerts = alertService.evaluateReading(reading);

        assertThat(alerts).hasSize(1);
        AlertResponse alert = alerts.get(0);
        assertThat(alert.alertType()).isEqualTo(AlertType.HIGH_TEMPERATURE);
        assertThat(alert.measuredValue()).isEqualByComparingTo("3.50");
        assertThat(alert.thresholdValue()).isEqualByComparingTo("2.50");
        assertThat(alert.message()).contains("Apples Standard");
    }

    @Test
    @DisplayName("Readings within assigned commodity profile limits trigger no alerts")
    void evaluateReading_WithinAssignedProfileLimits_GeneratesNoAlerts() {
        CommodityProfile applesProfile = new CommodityProfile(
                "Apples Standard",
                "Apples",
                "Fuji",
                new BigDecimal("0.50"),
                new BigDecimal("2.50"),
                new BigDecimal("85.00"),
                new BigDecimal("95.00")
        );
        applesProfile.setId(5L);
        applesProfile.setActive(true);
        facility.setCommodityProfile(applesProfile);

        SensorReading reading = new SensorReading(sensor, new BigDecimal("1.50"), new BigDecimal("90.00"), LocalDateTime.now(), ReadingDataSource.DEVICE);

        List<AlertResponse> alerts = alertService.evaluateReading(reading);

        assertThat(alerts).isEmpty();
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Inactive assigned commodity profile falls back to global thresholds")
    void evaluateReading_WithInactiveCommodityProfile_FallsBackToGlobalThresholds() {
        CommodityProfile inactiveProfile = new CommodityProfile(
                "Inactive Profile",
                "Apples",
                null,
                new BigDecimal("0.50"),
                new BigDecimal("2.50"),
                new BigDecimal("85.00"),
                new BigDecimal("95.00")
        );
        inactiveProfile.setId(5L);
        inactiveProfile.setActive(false);
        facility.setCommodityProfile(inactiveProfile);

        // 3.50 °C is above profile max (2.50 °C), but within global max (8.00 °C) -> No alert because profile is inactive
        SensorReading reading = new SensorReading(sensor, new BigDecimal("3.50"), new BigDecimal("80.00"), LocalDateTime.now(), ReadingDataSource.DEVICE);

        List<AlertResponse> alerts = alertService.evaluateReading(reading);

        assertThat(alerts).isEmpty();
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Facility without assigned profile falls back to global thresholds")
    void evaluateReading_NoProfile_FallsBackToGlobalThresholds() {
        facility.setCommodityProfile(null);

        // 9.00 °C breaches global max (8.00 °C)
        SensorReading reading = new SensorReading(sensor, new BigDecimal("9.00"), new BigDecimal("80.00"), LocalDateTime.now(), ReadingDataSource.DEVICE);

        when(alertRepository.existsBySensorIdAndAlertTypeAndStatus(100L, AlertType.HIGH_TEMPERATURE, AlertStatus.OPEN))
                .thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            a.setId(20L);
            return a;
        });

        List<AlertResponse> alerts = alertService.evaluateReading(reading);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).thresholdValue()).isEqualByComparingTo("8.00");
    }

    @Test
    @DisplayName("Low humidity breach under assigned commodity profile")
    void evaluateReading_LowHumidityUnderProfile_CreatesAlert() {
        CommodityProfile profile = new CommodityProfile(
                "High Moisture Veggies",
                "Leafy Greens",
                null,
                new BigDecimal("1.00"),
                new BigDecimal("4.00"),
                new BigDecimal("90.00"),
                new BigDecimal("98.00")
        );
        profile.setId(7L);
        profile.setActive(true);
        facility.setCommodityProfile(profile);

        // 80% humidity is safe under global thresholds (65-95%), but is below profile min of 90%
        SensorReading reading = new SensorReading(sensor, new BigDecimal("2.00"), new BigDecimal("80.00"), LocalDateTime.now(), ReadingDataSource.DEVICE);

        when(alertRepository.existsBySensorIdAndAlertTypeAndStatus(100L, AlertType.LOW_HUMIDITY, AlertStatus.OPEN))
                .thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            a.setId(25L);
            return a;
        });

        List<AlertResponse> alerts = alertService.evaluateReading(reading);

        assertThat(alerts).hasSize(1);
        AlertResponse alert = alerts.get(0);
        assertThat(alert.alertType()).isEqualTo(AlertType.LOW_HUMIDITY);
        assertThat(alert.measuredValue()).isEqualByComparingTo("80.00");
        assertThat(alert.thresholdValue()).isEqualByComparingTo("90.00");
        assertThat(alert.message()).contains("High Moisture Veggies");
    }
}

