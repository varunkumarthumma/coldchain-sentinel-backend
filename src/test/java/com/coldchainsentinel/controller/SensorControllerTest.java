package com.coldchainsentinel.controller;

import com.coldchainsentinel.ColdChainSentinelApplication;
import com.coldchainsentinel.dto.CreateSensorRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.SensorResponse;
import com.coldchainsentinel.dto.UpdateSensorRequest;
import com.coldchainsentinel.exception.GlobalExceptionHandler;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.SensorType;
import com.coldchainsentinel.service.SensorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@WebMvcTest(SensorController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {ColdChainSentinelApplication.class, SensorController.class, GlobalExceptionHandler.class})
class SensorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SensorService sensorService;

    private SensorResponse sampleResponse() {
        return new SensorResponse(
                1L,
                "SN-TEMP-001",
                "Ambient Temp Sensor",
                SensorType.TEMPERATURE,
                10L,
                "Central Cold Hub",
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("POST /api/sensors registers sensor and returns 201 Created")
    void shouldRegisterSensor() throws Exception {
        CreateSensorRequest request = new CreateSensorRequest(
                "SN-TEMP-001",
                "Ambient Temp Sensor",
                SensorType.TEMPERATURE,
                10L
        );

        when(sensorService.registerSensor(any(CreateSensorRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.sensorCode").value("SN-TEMP-001"))
                .andExpect(jsonPath("$.facilityId").value(10L))
                .andExpect(jsonPath("$.sensorType").value("TEMPERATURE"));
    }

    @Test
    @DisplayName("POST /api/sensors returns 400 Bad Request on invalid input")
    void shouldReturn400OnInvalidInput() throws Exception {
        CreateSensorRequest invalidRequest = new CreateSensorRequest(
                "",
                "",
                null,
                null
        );

        mockMvc.perform(post("/api/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @DisplayName("POST /api/sensors returns 404 Not Found when facility does not exist")
    void shouldReturn404WhenFacilityNotFoundOnRegister() throws Exception {
        CreateSensorRequest request = new CreateSensorRequest(
                "SN-TEMP-001",
                "Ambient Temp Sensor",
                SensorType.TEMPERATURE,
                999L
        );

        when(sensorService.registerSensor(any(CreateSensorRequest.class)))
                .thenThrow(new ResourceNotFoundException("Storage facility not found with id: 999"));

        mockMvc.perform(post("/api/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Storage facility not found with id: 999"));
    }

    @Test
    @DisplayName("GET /api/sensors returns 200 OK and paginated response")
    void shouldGetAllSensors() throws Exception {
        PagedResponse<SensorResponse> pagedResponse = new PagedResponse<>(
                List.of(sampleResponse()),
                0,
                10,
                1,
                1,
                true,
                true
        );

        when(sensorService.getAllSensors(any())).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/sensors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].sensorCode").value("SN-TEMP-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/sensors/{id} returns 200 OK when sensor exists")
    void shouldGetSensorById() throws Exception {
        when(sensorService.getSensorById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/sensors/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.sensorCode").value("SN-TEMP-001"));
    }

    @Test
    @DisplayName("GET /api/sensors/{id} returns 404 Not Found when sensor not found")
    void shouldReturn404WhenSensorNotFound() throws Exception {
        when(sensorService.getSensorById(999L))
                .thenThrow(new ResourceNotFoundException("Sensor not found with id: 999"));

        mockMvc.perform(get("/api/sensors/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/facilities/{facilityId}/sensors returns 200 OK with facility sensors")
    void shouldGetSensorsByFacility() throws Exception {
        when(sensorService.getSensorsByFacility(10L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/facilities/10/sensors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].facilityId").value(10L));
    }

    @Test
    @DisplayName("GET /api/facilities/{facilityId}/sensors returns 404 when facility does not exist")
    void shouldReturn404WhenFacilityNotFoundForSensors() throws Exception {
        when(sensorService.getSensorsByFacility(999L))
                .thenThrow(new ResourceNotFoundException("Storage facility not found with id: 999"));

        mockMvc.perform(get("/api/facilities/999/sensors"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PUT /api/sensors/{id} updates sensor and returns 200 OK")
    void shouldUpdateSensor() throws Exception {
        UpdateSensorRequest request = new UpdateSensorRequest(
                "Updated Sensor Name",
                SensorType.TEMPERATURE_HUMIDITY,
                10L,
                true
        );

        when(sensorService.updateSensor(eq(1L), any(UpdateSensorRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/sensors/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/sensors/{id}/deactivate deactivates sensor and returns 200 OK")
    void shouldDeactivateSensor() throws Exception {
        SensorResponse deactivated = new SensorResponse(
                1L,
                "SN-TEMP-001",
                "Ambient Temp Sensor",
                SensorType.TEMPERATURE,
                10L,
                "Central Cold Hub",
                false,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(sensorService.deactivateSensor(1L)).thenReturn(deactivated);

        mockMvc.perform(patch("/api/sensors/1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("POST /api/sensors/{id}/token provisions new device token and returns 200 OK")
    void shouldProvisionToken() throws Exception {
        com.coldchainsentinel.dto.ProvisionDeviceTokenResponse provisionResponse =
                com.coldchainsentinel.dto.ProvisionDeviceTokenResponse.of(1L, "SN-TEMP-001", "cct_test_token_abcdef123456");

        when(sensorService.provisionDeviceToken(1L)).thenReturn(provisionResponse);

        mockMvc.perform(post("/api/sensors/1/token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorId").value(1L))
                .andExpect(jsonPath("$.sensorCode").value("SN-TEMP-001"))
                .andExpect(jsonPath("$.deviceToken").value("cct_test_token_abcdef123456"))
                .andExpect(jsonPath("$.instructions").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/sensors/{id}/token returns 403 Forbidden when provisioning is disabled in production")
    void shouldReturn403WhenProvisioningDisabledInProduction() throws Exception {
        when(sensorService.provisionDeviceToken(1L))
                .thenThrow(new com.coldchainsentinel.exception.ProvisioningDisabledException(
                        "Device token provisioning is disabled in production without administrative authorization."
                ));

        mockMvc.perform(post("/api/sensors/1/token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Device token provisioning is disabled in production without administrative authorization."));
    }
}
