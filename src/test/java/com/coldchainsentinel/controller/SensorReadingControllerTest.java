package com.coldchainsentinel.controller;

import com.coldchainsentinel.ColdChainSentinelApplication;
import com.coldchainsentinel.dto.CreateSensorReadingRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.SensorReadingResponse;
import com.coldchainsentinel.exception.GlobalExceptionHandler;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.ReadingDataSource;
import com.coldchainsentinel.service.SensorReadingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SensorReadingController.class)
@ContextConfiguration(classes = {ColdChainSentinelApplication.class, SensorReadingController.class, GlobalExceptionHandler.class})
class SensorReadingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SensorReadingService readingService;

    private SensorReadingResponse sampleResponse() {
        return new SensorReadingResponse(
                100L,
                1L,
                "SN-T-01",
                new BigDecimal("4.50"),
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                ReadingDataSource.DEVICE
        );
    }

    @Test
    @DisplayName("POST /api/sensors/{sensorId}/readings records reading and returns 201 Created")
    void shouldRecordReading() throws Exception {
        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("4.50"),
                null,
                LocalDateTime.now(),
                ReadingDataSource.DEVICE
        );

        when(readingService.recordReading(eq(1L), any(CreateSensorReadingRequest.class), any()))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/sensors/1/readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.sensorId").value(1L))
                .andExpect(jsonPath("$.temperatureCelsius").value(4.50))
                .andExpect(jsonPath("$.dataSource").value("DEVICE"));
    }

    @Test
    @DisplayName("POST /api/sensors/{sensorId}/readings returns 400 on out-of-range measurement")
    void shouldReturn400OnOutOfRangeValue() throws Exception {
        CreateSensorReadingRequest invalid = new CreateSensorReadingRequest(
                new BigDecimal("150.00"), // Max is 100.00
                null,
                LocalDateTime.now(),
                ReadingDataSource.DEVICE
        );

        mockMvc.perform(post("/api/sensors/1/readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @DisplayName("POST /api/sensors/{sensorId}/readings returns 400 on sensor type mismatch")
    void shouldReturn400OnSensorTypeMismatch() throws Exception {
        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("4.50"),
                new BigDecimal("50.00"),
                LocalDateTime.now(),
                ReadingDataSource.DEVICE
        );

        when(readingService.recordReading(eq(1L), any(CreateSensorReadingRequest.class), any()))
                .thenThrow(new IllegalArgumentException("Temperature-only sensor cannot accept humidity measurements"));

        mockMvc.perform(post("/api/sensors/1/readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Temperature-only sensor cannot accept humidity measurements"));
    }

    @Test
    @DisplayName("POST /api/sensors/{sensorId}/readings returns 404 when sensor missing")
    void shouldReturn404WhenSensorMissing() throws Exception {
        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("4.50"),
                null,
                LocalDateTime.now(),
                ReadingDataSource.DEVICE
        );

        when(readingService.recordReading(eq(999L), any(CreateSensorReadingRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Sensor not found with id: 999"));

        mockMvc.perform(post("/api/sensors/999/readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/sensors/{sensorId}/readings returns 200 OK with paginated readings")
    void shouldGetReadingsBySensor() throws Exception {
        PagedResponse<SensorReadingResponse> pagedResponse = new PagedResponse<>(
                List.of(sampleResponse()),
                0,
                20,
                1,
                1,
                true,
                true
        );

        when(readingService.getReadingsBySensor(eq(1L), any())).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/sensors/1/readings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(100L))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/sensors/{sensorId}/readings returns 404 when sensor missing")
    void shouldReturn404WhenSensorMissingForReadings() throws Exception {
        when(readingService.getReadingsBySensor(eq(999L), any()))
                .thenThrow(new ResourceNotFoundException("Sensor not found with id: 999"));

        mockMvc.perform(get("/api/sensors/999/readings"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/readings/{readingId} returns 200 OK when reading exists")
    void shouldGetReadingById() throws Exception {
        when(readingService.getReadingById(100L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/readings/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.sensorCode").value("SN-T-01"));
    }

    @Test
    @DisplayName("GET /api/readings/{readingId} returns 404 when reading missing")
    void shouldReturn404WhenReadingMissing() throws Exception {
        when(readingService.getReadingById(999L))
                .thenThrow(new ResourceNotFoundException("Sensor reading not found with id: 999"));

        mockMvc.perform(get("/api/readings/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/sensors/{sensorId}/readings/range returns 200 OK with readings in range")
    void shouldGetReadingsByRange() throws Exception {
        when(readingService.getReadingsBySensorAndRange(eq(1L), any(), any()))
                .thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/sensors/1/readings/range")
                        .param("from", "2026-10-11T00:00:00")
                        .param("to", "2026-10-12T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(100L));
    }

    @Test
    @DisplayName("GET /api/sensors/{sensorId}/readings/range returns 400 when from after to")
    void shouldReturn400WhenFromAfterTo() throws Exception {
        when(readingService.getReadingsBySensorAndRange(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("'from' timestamp must be before or equal to 'to' timestamp"));

        mockMvc.perform(get("/api/sensors/1/readings/range")
                        .param("from", "2026-10-12T00:00:00")
                        .param("to", "2026-10-11T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("'from' timestamp must be before or equal to 'to' timestamp"));
    }

    @Test
    @DisplayName("POST /api/sensors/{sensorId}/readings/batch records batch and returns 201 Created")
    void shouldRecordBatchReadings() throws Exception {
        CreateSensorReadingRequest r1 = new CreateSensorReadingRequest(
                new BigDecimal("4.50"), null, LocalDateTime.now(), ReadingDataSource.DEVICE
        );
        CreateSensorReadingRequest r2 = new CreateSensorReadingRequest(
                new BigDecimal("5.00"), null, LocalDateTime.now(), ReadingDataSource.DEVICE
        );

        com.coldchainsentinel.dto.BatchSensorReadingResponse batchResponse = new com.coldchainsentinel.dto.BatchSensorReadingResponse(
                1L,
                "SN-T-01",
                2,
                List.of(sampleResponse(), sampleResponse())
        );

        when(readingService.recordReadingsBatch(eq(1L), any(), any()))
                .thenReturn(batchResponse);

        mockMvc.perform(post("/api/sensors/1/readings/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(r1, r2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sensorId").value(1L))
                .andExpect(jsonPath("$.sensorCode").value("SN-T-01"))
                .andExpect(jsonPath("$.totalIngested").value(2))
                .andExpect(jsonPath("$.readings").isArray())
                .andExpect(jsonPath("$.readings.length()").value(2));
    }

    @Test
    @DisplayName("POST /api/sensors/{sensorId}/readings/batch returns 400 when batch is empty")
    void shouldReturn400WhenBatchIsEmpty() throws Exception {
        when(readingService.recordReadingsBatch(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("Batch payload cannot be empty or null"));

        mockMvc.perform(post("/api/sensors/1/readings/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Batch payload cannot be empty or null"));
    }

    @Test
    @DisplayName("POST /api/sensors/{sensorId}/readings/batch returns 400 when batch is oversized")
    void shouldReturn400WhenBatchIsOversized() throws Exception {
        when(readingService.recordReadingsBatch(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("Batch size of 501 exceeds maximum allowed limit of 500"));

        mockMvc.perform(post("/api/sensors/1/readings/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Batch size of 501 exceeds maximum allowed limit of 500"));
    }

    @Test
    @DisplayName("POST /api/sensors/{sensorId}/readings/batch returns 400 when sensor is inactive")
    void shouldReturn400WhenSensorIsInactiveForBatch() throws Exception {
        when(readingService.recordReadingsBatch(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("Sensor is inactive and cannot accept readings. Sensor ID: 1"));

        mockMvc.perform(post("/api/sensors/1/readings/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sensor is inactive and cannot accept readings. Sensor ID: 1"));
    }

    @Test
    @DisplayName("POST /api/sensors/{sensorId}/readings returns 401 when token is missing or invalid")
    void shouldReturn401WhenDeviceTokenIsMissingOrInvalid() throws Exception {
        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("4.50"), null, LocalDateTime.now(), ReadingDataSource.DEVICE
        );

        when(readingService.recordReading(eq(1L), any(CreateSensorReadingRequest.class), any()))
                .thenThrow(new com.coldchainsentinel.exception.UnauthorizedDeviceException("Missing required 'X-Sensor-Token' header for provisioned sensor: SN-T-01"));

        mockMvc.perform(post("/api/sensors/1/readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Missing required 'X-Sensor-Token' header for provisioned sensor: SN-T-01"));
    }

    @Test
    @DisplayName("POST /api/sensors/{sensorId}/readings/batch returns 401 when token is invalid")
    void shouldReturn401WhenBatchDeviceTokenIsInvalid() throws Exception {
        when(readingService.recordReadingsBatch(eq(1L), any(), any()))
                .thenThrow(new com.coldchainsentinel.exception.UnauthorizedDeviceException("Invalid device token for sensor: SN-T-01"));

        mockMvc.perform(post("/api/sensors/1/readings/batch")
                        .header("X-Sensor-Token", "invalid_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{}]"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid device token for sensor: SN-T-01"));
    }

    @Test
    @DisplayName("POST /api/sensors/{sensorId}/readings returns 401 when unprovisioned sensor is rejected in production")
    void shouldReturn401WhenUnprovisionedSensorRejectedInProduction() throws Exception {
        CreateSensorReadingRequest request = new CreateSensorReadingRequest(
                new BigDecimal("4.50"), null, LocalDateTime.now(), ReadingDataSource.DEVICE
        );

        when(readingService.recordReading(eq(1L), any(CreateSensorReadingRequest.class), any()))
                .thenThrow(new com.coldchainsentinel.exception.UnauthorizedDeviceException("Unprovisioned sensor 'SN-T-01' cannot submit telemetry. Unauthenticated ingestion is disabled in production."));

        mockMvc.perform(post("/api/sensors/1/readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Unprovisioned sensor 'SN-T-01' cannot submit telemetry. Unauthenticated ingestion is disabled in production."));
    }

    @Test
    @DisplayName("POST /api/sensors/{sensorId}/readings/batch returns 401 when unprovisioned sensor is rejected in production")
    void shouldReturn401WhenBatchUnprovisionedSensorRejectedInProduction() throws Exception {
        when(readingService.recordReadingsBatch(eq(1L), any(), any()))
                .thenThrow(new com.coldchainsentinel.exception.UnauthorizedDeviceException("Unprovisioned sensor 'SN-T-01' cannot submit telemetry. Unauthenticated ingestion is disabled in production."));

        mockMvc.perform(post("/api/sensors/1/readings/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{}]"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Unprovisioned sensor 'SN-T-01' cannot submit telemetry. Unauthenticated ingestion is disabled in production."));
    }
}
