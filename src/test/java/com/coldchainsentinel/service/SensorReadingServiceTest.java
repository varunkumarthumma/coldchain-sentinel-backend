package com.coldchainsentinel.service;

import com.coldchainsentinel.dto.CreateSensorReadingRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.SensorReadingResponse;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.ReadingDataSource;
import com.coldchainsentinel.model.Sensor;
import com.coldchainsentinel.model.SensorReading;
import com.coldchainsentinel.model.SensorType;
import com.coldchainsentinel.model.StorageFacility;
import com.coldchainsentinel.repository.SensorReadingRepository;
import com.coldchainsentinel.repository.SensorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
class SensorReadingServiceTest {

    @Mock
    private SensorReadingRepository readingRepository;

    @Mock
    private SensorRepository sensorRepository;

    @Mock
    private AlertService alertService;

    private jakarta.validation.Validator validator;

    private SensorReadingService readingService;

    private Sensor tempSensor;
    private Sensor humiditySensor;
    private Sensor comboSensor;
    private SensorReading sampleReading;

    @BeforeEach
    void setUp() {
        jakarta.validation.ValidatorFactory factory = jakarta.validation.Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        readingService = new SensorReadingService(readingRepository, sensorRepository, alertService, validator,
                new com.coldchainsentinel.config.DeviceSecurityProperties(true, true));

        StorageFacility facility = new StorageFacility();
        facility.setId(10L);
        facility.setName("Central Cold Hub");

        tempSensor = new Sensor("SN-T-01", "Temp Sensor", SensorType.TEMPERATURE, facility);
        tempSensor.setId(1L);
        tempSensor.setActive(true);

        humiditySensor = new Sensor("SN-H-01", "Humidity Sensor", SensorType.HUMIDITY, facility);
        humiditySensor.setId(2L);
        humiditySensor.setActive(true);

        comboSensor = new Sensor("SN-TH-01", "Combo Sensor", SensorType.TEMPERATURE_HUMIDITY, facility);
        comboSensor.setId(3L);
        comboSensor.setActive(true);

        sampleReading = new SensorReading(
                comboSensor,
                new BigDecimal("4.50"),
                new BigDecimal("65.20"),
                LocalDateTime.now(),
                ReadingDataSource.DEVICE
        );
        sampleReading.setId(100L);
        sampleReading.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("recordReading succeeds for temperature-only sensor")
    void shouldRecordTemperatureReading() {
        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("3.20"),
                null,
                LocalDateTime.now(),
                ReadingDataSource.DEVICE
        );

        when(sensorRepository.findById(1L)).thenReturn(Optional.of(tempSensor));
        when(readingRepository.save(any(SensorReading.class))).thenAnswer(invocation -> {
            SensorReading r = invocation.getArgument(0);
            r.setId(101L);
            return r;
        });

        SensorReadingResponse response = readingService.recordReading(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.temperatureCelsius()).isEqualTo(new BigDecimal("3.20"));
        assertThat(response.humidityPercentage()).isNull();
        assertThat(response.dataSource()).isEqualTo(ReadingDataSource.DEVICE);
    }

    @Test
    @DisplayName("recordReading succeeds for humidity-only sensor")
    void shouldRecordHumidityReading() {
        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                null,
                new BigDecimal("55.00"),
                LocalDateTime.now(),
                ReadingDataSource.SIMULATED
        );

        when(sensorRepository.findById(2L)).thenReturn(Optional.of(humiditySensor));
        when(readingRepository.save(any(SensorReading.class))).thenAnswer(invocation -> {
            SensorReading r = invocation.getArgument(0);
            r.setId(102L);
            return r;
        });

        SensorReadingResponse response = readingService.recordReading(2L, request);

        assertThat(response).isNotNull();
        assertThat(response.temperatureCelsius()).isNull();
        assertThat(response.humidityPercentage()).isEqualTo(new BigDecimal("55.00"));
        assertThat(response.dataSource()).isEqualTo(ReadingDataSource.SIMULATED);
    }

    @Test
    @DisplayName("recordReading succeeds for combo sensor with both values")
    void shouldRecordComboReading() {
        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("-2.50"),
                new BigDecimal("80.00"),
                LocalDateTime.now(),
                ReadingDataSource.DEVICE
        );

        when(sensorRepository.findById(3L)).thenReturn(Optional.of(comboSensor));
        when(readingRepository.save(any(SensorReading.class))).thenAnswer(invocation -> {
            SensorReading r = invocation.getArgument(0);
            r.setId(103L);
            return r;
        });

        SensorReadingResponse response = readingService.recordReading(3L, request);

        assertThat(response).isNotNull();
        assertThat(response.temperatureCelsius()).isEqualTo(new BigDecimal("-2.50"));
        assertThat(response.humidityPercentage()).isEqualTo(new BigDecimal("80.00"));
    }

    @Test
    @DisplayName("recordReading rejects humidity measurement on temperature-only sensor")
    void shouldRejectHumidityOnTempSensor() {
        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("5.00"),
                new BigDecimal("50.00"),
                LocalDateTime.now(),
                ReadingDataSource.DEVICE
        );

        when(sensorRepository.findById(1L)).thenReturn(Optional.of(tempSensor));

        assertThatThrownBy(() -> readingService.recordReading(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Temperature-only sensor cannot accept humidity measurements");

        verify(readingRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordReading rejects temperature measurement on humidity-only sensor")
    void shouldRejectTempOnHumiditySensor() {
        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("5.00"),
                new BigDecimal("50.00"),
                LocalDateTime.now(),
                ReadingDataSource.DEVICE
        );

        when(sensorRepository.findById(2L)).thenReturn(Optional.of(humiditySensor));

        assertThatThrownBy(() -> readingService.recordReading(2L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Humidity-only sensor cannot accept temperature measurements");

        verify(readingRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordReading rejects when both measurements are null")
    void shouldRejectWhenBothMeasurementsNull() {
        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                null,
                null,
                LocalDateTime.now(),
                ReadingDataSource.SIMULATED
        );

        when(sensorRepository.findById(1L)).thenReturn(Optional.of(tempSensor));

        assertThatThrownBy(() -> readingService.recordReading(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one measurement");
    }

    @Test
    @DisplayName("recordReading rejects readings for inactive sensor")
    void shouldRejectInactiveSensor() {
        tempSensor.setActive(false);
        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("4.00"),
                null,
                LocalDateTime.now(),
                ReadingDataSource.DEVICE
        );

        when(sensorRepository.findById(1L)).thenReturn(Optional.of(tempSensor));

        assertThatThrownBy(() -> readingService.recordReading(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inactive");

        verify(readingRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordReading throws ResourceNotFoundException when sensor missing")
    void shouldThrowWhenSensorMissing() {
        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("4.00"),
                null,
                LocalDateTime.now(),
                ReadingDataSource.DEVICE
        );

        when(sensorRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> readingService.recordReading(999L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("getReadingById returns response when reading exists")
    void shouldGetReadingById() {
        when(readingRepository.findById(100L)).thenReturn(Optional.of(sampleReading));

        SensorReadingResponse response = readingService.getReadingById(100L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.sensorCode()).isEqualTo("SN-TH-01");
    }

    @Test
    @DisplayName("getReadingById throws ResourceNotFoundException when reading missing")
    void shouldThrowWhenReadingMissing() {
        when(readingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> readingService.getReadingById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("getReadingsBySensor returns paginated readings newest first")
    void shouldGetReadingsBySensorPaginated() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<SensorReading> page = new PageImpl<>(List.of(sampleReading), pageable, 1);

        when(sensorRepository.existsById(3L)).thenReturn(true);
        when(readingRepository.findBySensorIdOrderByRecordedAtDesc(3L, pageable)).thenReturn(page);

        PagedResponse<SensorReadingResponse> response = readingService.getReadingsBySensor(3L, pageable);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getReadingsBySensorAndRange returns readings within valid range")
    void shouldGetReadingsBySensorAndRange() {
        LocalDateTime from = LocalDateTime.now().minusHours(2);
        LocalDateTime to = LocalDateTime.now();

        when(sensorRepository.existsById(3L)).thenReturn(true);
        when(readingRepository.findBySensorIdAndRecordedAtBetweenOrderByRecordedAtDesc(3L, from, to))
                .thenReturn(List.of(sampleReading));

        List<SensorReadingResponse> list = readingService.getReadingsBySensorAndRange(3L, from, to);

        assertThat(list).hasSize(1);
    }

    @Test
    @DisplayName("getReadingsBySensorAndRange rejects when from is after to")
    void shouldRejectWhenFromAfterTo() {
        LocalDateTime from = LocalDateTime.now();
        LocalDateTime to = LocalDateTime.now().minusHours(2);

        when(sensorRepository.existsById(3L)).thenReturn(true);

        assertThatThrownBy(() -> readingService.getReadingsBySensorAndRange(3L, from, to))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be before or equal to");
    }

    @Test
    @DisplayName("recordReadingsBatch persists all readings and evaluates alerts for each")
    void recordReadingsBatch_ValidBatch_PersistsAndEvaluatesAlerts() {
        when(sensorRepository.findById(3L)).thenReturn(Optional.of(comboSensor));

        CreateSensorReadingRequest r1 = new CreateSensorReadingRequest(
                new BigDecimal("4.20"), new BigDecimal("75.0"), LocalDateTime.now().minusMinutes(5), ReadingDataSource.DEVICE
        );
        CreateSensorReadingRequest r2 = new CreateSensorReadingRequest(
                new BigDecimal("12.50"), new BigDecimal("78.0"), LocalDateTime.now(), ReadingDataSource.DEVICE
        );

        when(readingRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<SensorReading> list = invocation.getArgument(0);
            long id = 500L;
            for (SensorReading sr : list) {
                sr.setId(id++);
            }
            return list;
        });

        var response = readingService.recordReadingsBatch(3L, List.of(r1, r2));

        assertThat(response.sensorId()).isEqualTo(3L);
        assertThat(response.totalIngested()).isEqualTo(2);
        assertThat(response.readings()).hasSize(2);
        assertThat(response.readings().get(0).temperatureCelsius()).isEqualByComparingTo(new BigDecimal("4.20"));
        assertThat(response.readings().get(1).temperatureCelsius()).isEqualByComparingTo(new BigDecimal("12.50"));

        verify(readingRepository).saveAll(anyList());
        verify(alertService, times(2)).evaluateReading(any(SensorReading.class));
    }

    @Test
    @DisplayName("recordReadingsBatch rejects empty or null batch")
    void recordReadingsBatch_EmptyBatch_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> readingService.recordReadingsBatch(3L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch payload cannot be empty or null");

        assertThatThrownBy(() -> readingService.recordReadingsBatch(3L, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch payload cannot be empty or null");
    }

    @Test
    @DisplayName("recordReadingsBatch rejects oversized batch exceeding MAX_BATCH_SIZE")
    void recordReadingsBatch_OversizedBatch_ThrowsIllegalArgumentException() {
        List<CreateSensorReadingRequest> oversized = new java.util.ArrayList<>();
        for (int i = 0; i < 501; i++) {
            oversized.add(new CreateSensorReadingRequest(
                    new BigDecimal("4.00"), new BigDecimal("70.0"), LocalDateTime.now(), ReadingDataSource.DEVICE
            ));
        }

        assertThatThrownBy(() -> readingService.recordReadingsBatch(3L, oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum allowed limit of 500");
    }

    @Test
    @DisplayName("recordReadingsBatch rejects when sensor is not found")
    void recordReadingsBatch_SensorNotFound_ThrowsResourceNotFoundException() {
        when(sensorRepository.findById(999L)).thenReturn(Optional.empty());

        CreateSensorReadingRequest r = new CreateSensorReadingRequest(
                new BigDecimal("4.00"), new BigDecimal("70.0"), LocalDateTime.now(), ReadingDataSource.DEVICE
        );

        assertThatThrownBy(() -> readingService.recordReadingsBatch(999L, List.of(r)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Sensor not found with id: 999");
    }

    @Test
    @DisplayName("recordReadingsBatch rejects when sensor is inactive")
    void recordReadingsBatch_InactiveSensor_ThrowsIllegalArgumentException() {
        Sensor inactiveSensor = new Sensor("SN-INACTIVE", "Inactive Sensor", SensorType.TEMPERATURE_HUMIDITY, null);
        inactiveSensor.setId(4L);
        inactiveSensor.setActive(false);

        when(sensorRepository.findById(4L)).thenReturn(Optional.of(inactiveSensor));

        CreateSensorReadingRequest r = new CreateSensorReadingRequest(
                new BigDecimal("4.00"), new BigDecimal("70.0"), LocalDateTime.now(), ReadingDataSource.DEVICE
        );

        assertThatThrownBy(() -> readingService.recordReadingsBatch(4L, List.of(r)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sensor is inactive and cannot accept readings. Sensor ID: 4");

        verifyNoInteractions(readingRepository);
    }

    @Test
    @DisplayName("recordReadingsBatch rejects when batch contains invalid reading values")
    void recordReadingsBatch_InvalidItemValues_ThrowsIllegalArgumentException() {
        when(sensorRepository.findById(3L)).thenReturn(Optional.of(comboSensor));

        // Temperature out of range (> 100 °C)
        CreateSensorReadingRequest invalidTemp = new CreateSensorReadingRequest(
                new BigDecimal("150.00"), new BigDecimal("70.0"), LocalDateTime.now(), ReadingDataSource.DEVICE
        );

        assertThatThrownBy(() -> readingService.recordReadingsBatch(3L, List.of(invalidTemp)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch item at index 0 is invalid");

        // Missing dataSource
        CreateSensorReadingRequest missingSource = new CreateSensorReadingRequest(
                new BigDecimal("4.00"), new BigDecimal("70.0"), LocalDateTime.now(), null
        );

        assertThatThrownBy(() -> readingService.recordReadingsBatch(3L, List.of(missingSource)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch item at index 0 is invalid");

        verifyNoInteractions(readingRepository);
    }

    @Test
    @DisplayName("recordReadingsBatch rejects when measurements do not match sensor type")
    void recordReadingsBatch_MismatchedSensorType_ThrowsIllegalArgumentException() {
        when(sensorRepository.findById(1L)).thenReturn(Optional.of(tempSensor));

        // Submitting humidity for temperature-only sensor
        CreateSensorReadingRequest withHumidity = new CreateSensorReadingRequest(
                new BigDecimal("4.00"), new BigDecimal("70.0"), LocalDateTime.now(), ReadingDataSource.DEVICE
        );

        assertThatThrownBy(() -> readingService.recordReadingsBatch(1L, List.of(withHumidity)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Temperature-only sensor cannot accept humidity measurements");

        verifyNoInteractions(readingRepository);
    }

    @Test
    @DisplayName("recordReading with provisioned sensor rejects missing token with UnauthorizedDeviceException")
    void recordReading_ProvisionedSensor_MissingToken_ThrowsUnauthorizedDeviceException() {
        String token = com.coldchainsentinel.security.DeviceTokenUtil.generateRawToken();
        comboSensor.setTokenHash(com.coldchainsentinel.security.DeviceTokenUtil.hashToken(token));

        when(sensorRepository.findById(3L)).thenReturn(Optional.of(comboSensor));

        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("4.00"), new BigDecimal("70.0"), LocalDateTime.now(), ReadingDataSource.DEVICE
        );

        assertThatThrownBy(() -> readingService.recordReading(3L, request, null))
                .isInstanceOf(com.coldchainsentinel.exception.UnauthorizedDeviceException.class)
                .hasMessageContaining("Missing required 'X-Sensor-Token' header");

        assertThatThrownBy(() -> readingService.recordReading(3L, request, ""))
                .isInstanceOf(com.coldchainsentinel.exception.UnauthorizedDeviceException.class)
                .hasMessageContaining("Missing required 'X-Sensor-Token' header");

        verifyNoInteractions(readingRepository);
    }

    @Test
    @DisplayName("recordReading with provisioned sensor rejects invalid token with UnauthorizedDeviceException")
    void recordReading_ProvisionedSensor_InvalidToken_ThrowsUnauthorizedDeviceException() {
        String validToken = com.coldchainsentinel.security.DeviceTokenUtil.generateRawToken();
        comboSensor.setTokenHash(com.coldchainsentinel.security.DeviceTokenUtil.hashToken(validToken));

        when(sensorRepository.findById(3L)).thenReturn(Optional.of(comboSensor));

        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("4.00"), new BigDecimal("70.0"), LocalDateTime.now(), ReadingDataSource.DEVICE
        );

        assertThatThrownBy(() -> readingService.recordReading(3L, request, "cct_invalid_random_token_1234567890"))
                .isInstanceOf(com.coldchainsentinel.exception.UnauthorizedDeviceException.class)
                .hasMessageContaining("Invalid device token for sensor");

        verifyNoInteractions(readingRepository);
    }

    @Test
    @DisplayName("recordReading with provisioned sensor rejects token belonging to another sensor (cross-sensor token use)")
    void recordReading_ProvisionedSensor_CrossSensorToken_ThrowsUnauthorizedDeviceException() {
        String sensor1Token = com.coldchainsentinel.security.DeviceTokenUtil.generateRawToken();
        String sensor2Token = com.coldchainsentinel.security.DeviceTokenUtil.generateRawToken();

        // Sensor 3 is provisioned with sensor1Token
        comboSensor.setTokenHash(com.coldchainsentinel.security.DeviceTokenUtil.hashToken(sensor1Token));

        when(sensorRepository.findById(3L)).thenReturn(Optional.of(comboSensor));

        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("4.00"), new BigDecimal("70.0"), LocalDateTime.now(), ReadingDataSource.DEVICE
        );

        // Attempt to authenticate Sensor 3 with sensor2Token
        assertThatThrownBy(() -> readingService.recordReading(3L, request, sensor2Token))
                .isInstanceOf(com.coldchainsentinel.exception.UnauthorizedDeviceException.class)
                .hasMessageContaining("Invalid device token for sensor");

        verifyNoInteractions(readingRepository);
    }

    @Test
    @DisplayName("recordReading with provisioned sensor succeeds when valid token is supplied")
    void recordReading_ProvisionedSensor_ValidToken_Succeeds() {
        String validToken = com.coldchainsentinel.security.DeviceTokenUtil.generateRawToken();
        comboSensor.setTokenHash(com.coldchainsentinel.security.DeviceTokenUtil.hashToken(validToken));

        when(sensorRepository.findById(3L)).thenReturn(Optional.of(comboSensor));
        when(readingRepository.save(any(SensorReading.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("4.00"), new BigDecimal("70.0"), LocalDateTime.now(), ReadingDataSource.DEVICE
        );

        var response = readingService.recordReading(3L, request, validToken);

        assertThat(response).isNotNull();
        assertThat(response.temperatureCelsius()).isEqualByComparingTo(new BigDecimal("4.00"));
        verify(readingRepository).save(any(SensorReading.class));
    }

    @Test
    @DisplayName("recordReadingsBatch with provisioned sensor rejects invalid token and succeeds with valid token")
    void recordReadingsBatch_ProvisionedSensor_TokenAuthentication() {
        String validToken = com.coldchainsentinel.security.DeviceTokenUtil.generateRawToken();
        comboSensor.setTokenHash(com.coldchainsentinel.security.DeviceTokenUtil.hashToken(validToken));

        when(sensorRepository.findById(3L)).thenReturn(Optional.of(comboSensor));

        CreateSensorReadingRequest r = new CreateSensorReadingRequest(
                new BigDecimal("4.00"), new BigDecimal("70.0"), LocalDateTime.now(), ReadingDataSource.DEVICE
        );

        // Rejection with missing token
        assertThatThrownBy(() -> readingService.recordReadingsBatch(3L, List.of(r), null))
                .isInstanceOf(com.coldchainsentinel.exception.UnauthorizedDeviceException.class);

        // Rejection with bad token
        assertThatThrownBy(() -> readingService.recordReadingsBatch(3L, List.of(r), "cct_wrong_token"))
                .isInstanceOf(com.coldchainsentinel.exception.UnauthorizedDeviceException.class);

        // Success with valid token
        when(readingRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        var batchResponse = readingService.recordReadingsBatch(3L, List.of(r), validToken);
        assertThat(batchResponse.totalIngested()).isEqualTo(1);
    }

    @Test
    @DisplayName("recordReading in production mode rejects unprovisioned sensor with 401 Unauthorized")
    void recordReading_ProductionMode_UnprovisionedSensor_ThrowsUnauthorizedDeviceException() {
        com.coldchainsentinel.config.DeviceSecurityProperties prodSecurity =
                new com.coldchainsentinel.config.DeviceSecurityProperties(false, false);
        SensorReadingService prodService = new SensorReadingService(readingRepository, sensorRepository, alertService, validator, prodSecurity);

        tempSensor.setTokenHash(null); // Unprovisioned sensor
        when(sensorRepository.findById(1L)).thenReturn(Optional.of(tempSensor));

        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("4.00"), null, LocalDateTime.now(), ReadingDataSource.DEVICE
        );

        assertThatThrownBy(() -> prodService.recordReading(1L, request))
                .isInstanceOf(com.coldchainsentinel.exception.UnauthorizedDeviceException.class)
                .hasMessageContaining("Unprovisioned sensor 'SN-T-01' cannot submit telemetry. Unauthenticated ingestion is disabled in production.");

        verifyNoInteractions(readingRepository);
    }

    @Test
    @DisplayName("recordReadingsBatch in production mode rejects unprovisioned sensor with 401 Unauthorized")
    void recordReadingsBatch_ProductionMode_UnprovisionedSensor_ThrowsUnauthorizedDeviceException() {
        com.coldchainsentinel.config.DeviceSecurityProperties prodSecurity =
                new com.coldchainsentinel.config.DeviceSecurityProperties(false, false);
        SensorReadingService prodService = new SensorReadingService(readingRepository, sensorRepository, alertService, validator, prodSecurity);

        tempSensor.setTokenHash(null); // Unprovisioned sensor
        when(sensorRepository.findById(1L)).thenReturn(Optional.of(tempSensor));

        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("4.00"), null, LocalDateTime.now(), ReadingDataSource.DEVICE
        );

        assertThatThrownBy(() -> prodService.recordReadingsBatch(1L, List.of(request)))
                .isInstanceOf(com.coldchainsentinel.exception.UnauthorizedDeviceException.class)
                .hasMessageContaining("Unprovisioned sensor 'SN-T-01' cannot submit telemetry. Unauthenticated ingestion is disabled in production.");

        verifyNoInteractions(readingRepository);
    }

    @Test
    @DisplayName("recordReading in dev mode permits unprovisioned sensor ingestion for simulator workflow")
    void recordReading_DevMode_UnprovisionedSensor_Succeeds() {
        tempSensor.setTokenHash(null); // Unprovisioned
        when(sensorRepository.findById(1L)).thenReturn(Optional.of(tempSensor));
        when(readingRepository.save(any(SensorReading.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("4.20"), null, LocalDateTime.now(), ReadingDataSource.SIMULATED
        );

        var response = readingService.recordReading(1L, request);
        assertThat(response).isNotNull();
        assertThat(response.temperatureCelsius()).isEqualByComparingTo(new BigDecimal("4.20"));
        assertThat(response.dataSource()).isEqualTo(ReadingDataSource.SIMULATED);
        verify(readingRepository).save(any(SensorReading.class));
    }

    @Test
    @DisplayName("recordReadingsBatch in dev mode permits unprovisioned sensor batch ingestion for simulator workflow")
    void recordReadingsBatch_DevMode_UnprovisionedSensor_Succeeds() {
        tempSensor.setTokenHash(null); // Unprovisioned
        when(sensorRepository.findById(1L)).thenReturn(Optional.of(tempSensor));
        when(readingRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("4.20"), null, LocalDateTime.now(), ReadingDataSource.SIMULATED
        );

        var response = readingService.recordReadingsBatch(1L, List.of(request));
        assertThat(response).isNotNull();
        assertThat(response.totalIngested()).isEqualTo(1);
        verify(readingRepository).saveAll(anyList());
    }
}
