package com.coldchainsentinel.service;

import com.coldchainsentinel.dto.CreateSensorRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.SensorResponse;
import com.coldchainsentinel.dto.UpdateSensorRequest;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.FacilityType;
import com.coldchainsentinel.model.Sensor;
import com.coldchainsentinel.model.SensorType;
import com.coldchainsentinel.model.StorageFacility;
import com.coldchainsentinel.repository.SensorRepository;
import com.coldchainsentinel.repository.StorageFacilityRepository;
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
class SensorServiceTest {

    @Mock
    private SensorRepository sensorRepository;

    @Mock
    private StorageFacilityRepository facilityRepository;

    @org.mockito.Spy
    private com.coldchainsentinel.config.DeviceSecurityProperties securityProperties = new com.coldchainsentinel.config.DeviceSecurityProperties(true, true);

    @InjectMocks
    private SensorService sensorService;

    private StorageFacility sampleFacility;
    private Sensor sampleSensor;

    @BeforeEach
    void setUp() {
        sampleFacility = new StorageFacility(
                "Central Cold Hub",
                "Sector 4, Hyderabad",
                new BigDecimal("500.00"),
                FacilityType.COLD_STORAGE
        );
        sampleFacility.setId(10L);
        sampleFacility.setActive(true);

        sampleSensor = new Sensor(
                "SN-TEMP-001",
                "Ambient Temperature Sensor 1",
                SensorType.TEMPERATURE,
                sampleFacility
        );
        sampleSensor.setId(1L);
        sampleSensor.setActive(true);
        sampleSensor.setCreatedAt(LocalDateTime.now());
        sampleSensor.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("registerSensor persists sensor when facility exists and code is unique")
    void shouldRegisterSensor() {
        CreateSensorRequest request = new CreateSensorRequest(
                "SN-TEMP-001",
                "Ambient Temperature Sensor 1",
                SensorType.TEMPERATURE,
                10L
        );

        when(facilityRepository.findById(10L)).thenReturn(Optional.of(sampleFacility));
        when(sensorRepository.existsBySensorCode("SN-TEMP-001")).thenReturn(false);
        when(sensorRepository.save(any(Sensor.class))).thenReturn(sampleSensor);

        SensorResponse response = sensorService.registerSensor(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.sensorCode()).isEqualTo("SN-TEMP-001");
        assertThat(response.facilityId()).isEqualTo(10L);
        assertThat(response.facilityName()).isEqualTo("Central Cold Hub");
        verify(sensorRepository).save(any(Sensor.class));
    }

    @Test
    @DisplayName("registerSensor throws ResourceNotFoundException when facility does not exist")
    void shouldThrowWhenFacilityNotFoundOnRegister() {
        CreateSensorRequest request = new CreateSensorRequest(
                "SN-TEMP-001",
                "Ambient Temperature Sensor 1",
                SensorType.TEMPERATURE,
                999L
        );

        when(facilityRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sensorService.registerSensor(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");

        verify(sensorRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerSensor throws IllegalArgumentException when sensor code already exists")
    void shouldThrowWhenSensorCodeExists() {
        CreateSensorRequest request = new CreateSensorRequest(
                "SN-TEMP-001",
                "Ambient Temperature Sensor 1",
                SensorType.TEMPERATURE,
                10L
        );

        when(facilityRepository.findById(10L)).thenReturn(Optional.of(sampleFacility));
        when(sensorRepository.existsBySensorCode("SN-TEMP-001")).thenReturn(true);

        assertThatThrownBy(() -> sensorService.registerSensor(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SN-TEMP-001");

        verify(sensorRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAllSensors returns paginated response")
    void shouldGetAllSensors() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Sensor> page = new PageImpl<>(List.of(sampleSensor), pageable, 1);

        when(sensorRepository.findAll(pageable)).thenReturn(page);

        PagedResponse<SensorResponse> response = sensorService.getAllSensors(pageable);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.page()).isEqualTo(0);
    }

    @Test
    @DisplayName("getSensorById returns response when sensor exists")
    void shouldGetSensorById() {
        when(sensorRepository.findById(1L)).thenReturn(Optional.of(sampleSensor));

        SensorResponse response = sensorService.getSensorById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.sensorCode()).isEqualTo("SN-TEMP-001");
    }

    @Test
    @DisplayName("getSensorById throws ResourceNotFoundException when sensor not found")
    void shouldThrowWhenSensorNotFound() {
        when(sensorRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sensorService.getSensorById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("getSensorsByFacility returns list when facility exists")
    void shouldGetSensorsByFacility() {
        when(facilityRepository.existsById(10L)).thenReturn(true);
        when(sensorRepository.findByFacilityId(10L)).thenReturn(List.of(sampleSensor));

        List<SensorResponse> response = sensorService.getSensorsByFacility(10L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).facilityId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("getSensorsByFacility throws ResourceNotFoundException when facility not found")
    void shouldThrowWhenFacilityNotFoundForSensors() {
        when(facilityRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> sensorService.getSensorsByFacility(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("updateSensor modifies sensor and facility reference")
    void shouldUpdateSensor() {
        UpdateSensorRequest request = new UpdateSensorRequest(
                "Updated Sensor Name",
                SensorType.TEMPERATURE_HUMIDITY,
                10L,
                true
        );

        when(sensorRepository.findById(1L)).thenReturn(Optional.of(sampleSensor));
        when(facilityRepository.findById(10L)).thenReturn(Optional.of(sampleFacility));
        when(sensorRepository.save(any(Sensor.class))).thenReturn(sampleSensor);

        SensorResponse response = sensorService.updateSensor(1L, request);

        assertThat(response).isNotNull();
        assertThat(sampleSensor.getName()).isEqualTo("Updated Sensor Name");
        assertThat(sampleSensor.getSensorType()).isEqualTo(SensorType.TEMPERATURE_HUMIDITY);
        verify(sensorRepository).save(sampleSensor);
    }

    @Test
    @DisplayName("deactivateSensor sets active to false without deleting")
    void shouldDeactivateSensor() {
        when(sensorRepository.findById(1L)).thenReturn(Optional.of(sampleSensor));
        when(sensorRepository.save(any(Sensor.class))).thenReturn(sampleSensor);

        SensorResponse response = sensorService.deactivateSensor(1L);

        assertThat(response).isNotNull();
        assertThat(sampleSensor.isActive()).isFalse();
        verify(sensorRepository).save(sampleSensor);
        verify(sensorRepository, never()).delete(any());
        verify(sensorRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("provisionDeviceToken generates raw token, persists SHA-256 hash, and delivers raw token")
    void shouldProvisionDeviceToken() {
        when(sensorRepository.findById(1L)).thenReturn(Optional.of(sampleSensor));
        when(sensorRepository.save(any(Sensor.class))).thenReturn(sampleSensor);

        var response = sensorService.provisionDeviceToken(1L);

        assertThat(response.sensorId()).isEqualTo(1L);
        assertThat(response.sensorCode()).isEqualTo("SN-TEMP-001");
        assertThat(response.deviceToken()).startsWith("cct_");
        assertThat(sampleSensor.getTokenHash()).isNotNull();
        assertThat(sampleSensor.getTokenHash()).hasSize(64);

        // Verify hash matches raw token
        assertThat(com.coldchainsentinel.security.DeviceTokenUtil.verifyToken(response.deviceToken(), sampleSensor.getTokenHash()))
                .isTrue();

        verify(sensorRepository).save(sampleSensor);
    }

    @Test
    @DisplayName("provisionDeviceToken throws ProvisioningDisabledException when open provisioning is disabled (production mode)")
    void shouldThrowProvisioningDisabledExceptionWhenOpenProvisioningDisabled() {
        com.coldchainsentinel.config.DeviceSecurityProperties prodSecurity =
                new com.coldchainsentinel.config.DeviceSecurityProperties(false, false);
        SensorService prodService = new SensorService(sensorRepository, facilityRepository, prodSecurity);

        assertThatThrownBy(() -> prodService.provisionDeviceToken(1L))
                .isInstanceOf(com.coldchainsentinel.exception.ProvisioningDisabledException.class)
                .hasMessageContaining("Device token provisioning is disabled in production");

        verify(sensorRepository, never()).save(any());
    }
}
