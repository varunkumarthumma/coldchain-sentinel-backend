package com.coldchainsentinel.controller;

import com.coldchainsentinel.ColdChainSentinelApplication;
import com.coldchainsentinel.dto.*;
import com.coldchainsentinel.exception.GlobalExceptionHandler;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.FacilityType;
import com.coldchainsentinel.model.RiskTier;
import com.coldchainsentinel.model.SensorType;
import com.coldchainsentinel.service.RiskAssessmentService;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RiskAssessmentController.class)
@ContextConfiguration(classes = {ColdChainSentinelApplication.class, RiskAssessmentController.class, GlobalExceptionHandler.class})
class RiskAssessmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RiskAssessmentService riskAssessmentService;

    private SensorRiskAssessmentResponse sampleSensorResponse(boolean sufficientData) {
        LocalDateTime now = LocalDateTime.now();
        RiskAssessmentResult result;
        if (sufficientData) {
            RiskDriverBreakdown drivers = new RiskDriverBreakdown(
                    new BigDecimal("35.00"),
                    new BigDecimal("10.00"),
                    new BigDecimal("15.00"),
                    new BigDecimal("25.00"),
                    new BigDecimal("4.50"),
                    180L,
                    90L,
                    new BigDecimal("12.50"),
                    new BigDecimal("3.00"),
                    new BigDecimal("85.00"),
                    new BigDecimal("60.00"),
                    1,
                    0
            );
            result = new RiskAssessmentResult(
                    true,
                    new BigDecimal("42.50"),
                    RiskTier.MODERATE,
                    "Assessment completed with 24 readings",
                    drivers,
                    List.of("Verify cooling system airflow and thermal door seals."),
                    now.minusHours(24),
                    now,
                    24,
                    RiskAssessmentResult.EXPERIMENTAL_DISCLAIMER
            );
        } else {
            result = new RiskAssessmentResult(
                    false,
                    null,
                    null,
                    "Insufficient telemetry data: 1 readings found; minimum required is 3",
                    null,
                    List.of("Ensure the sensor is active and reporting telemetry consistently."),
                    now.minusHours(24),
                    now,
                    1,
                    RiskAssessmentResult.EXPERIMENTAL_DISCLAIMER
            );
        }

        return new SensorRiskAssessmentResponse(
                10L,
                "SN-CH-01",
                "Chamber 1 Sensor",
                SensorType.TEMPERATURE_HUMIDITY,
                1L,
                "Central Cold Storage",
                24,
                now.minusHours(24),
                now,
                result
        );
    }

    private FacilityRiskAssessmentResponse sampleFacilityResponse() {
        LocalDateTime now = LocalDateTime.now();
        SensorRiskAssessmentResponse sensorResponse = sampleSensorResponse(true);
        SensorRiskSummary hotspot = new SensorRiskSummary(
                10L,
                "SN-CH-01",
                "Chamber 1 Sensor",
                true,
                new BigDecimal("42.50"),
                RiskTier.MODERATE
        );

        return new FacilityRiskAssessmentResponse(
                1L,
                "Central Cold Storage",
                FacilityType.COLD_STORAGE,
                "District Zone 4",
                24,
                now.minusHours(24),
                now,
                1,
                1,
                0,
                new BigDecimal("42.50"),
                RiskTier.MODERATE,
                List.of(hotspot),
                List.of(sensorResponse),
                List.of("All monitored zones are stable within normal safe operational boundaries."),
                "Facility risk score is calculated using conservative hotspot weighting",
                RiskAssessmentResult.EXPERIMENTAL_DISCLAIMER
        );
    }

    @Test
    @DisplayName("GET /api/sensors/{sensorId}/risk-assessment returns HTTP 200 with sensor assessment")
    void shouldGetSensorRiskAssessment() throws Exception {
        when(riskAssessmentService.assessSensorRisk(10L, 24)).thenReturn(sampleSensorResponse(true));

        mockMvc.perform(get("/api/sensors/{sensorId}/risk-assessment", 10L)
                        .param("windowHours", "24")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorId").value(10))
                .andExpect(jsonPath("$.sensorCode").value("SN-CH-01"))
                .andExpect(jsonPath("$.assessment.sufficientData").value(true))
                .andExpect(jsonPath("$.assessment.overallRiskScore").value(42.50))
                .andExpect(jsonPath("$.assessment.riskTier").value("MODERATE"));
    }

    @Test
    @DisplayName("GET /api/sensors/{sensorId}/risk-assessment returns 200 with sufficientData=false on sparse data")
    void shouldReturn200WithInsufficientData() throws Exception {
        when(riskAssessmentService.assessSensorRisk(10L, 24)).thenReturn(sampleSensorResponse(false));

        mockMvc.perform(get("/api/sensors/{sensorId}/risk-assessment", 10L)
                        .param("windowHours", "24")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessment.sufficientData").value(false))
                .andExpect(jsonPath("$.assessment.overallRiskScore").isEmpty())
                .andExpect(jsonPath("$.assessment.riskTier").isEmpty())
                .andExpect(jsonPath("$.assessment.statusMessage").value("Insufficient telemetry data: 1 readings found; minimum required is 3"));
    }

    @Test
    @DisplayName("GET /api/sensors/{sensorId}/risk-assessment returns 404 when sensor not found")
    void shouldReturn404WhenSensorNotFound() throws Exception {
        when(riskAssessmentService.assessSensorRisk(999L, 24))
                .thenThrow(new ResourceNotFoundException("Sensor not found with id: 999"));

        mockMvc.perform(get("/api/sensors/{sensorId}/risk-assessment", 999L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Sensor not found with id: 999"));
    }

    @Test
    @DisplayName("GET /api/sensors/{sensorId}/risk-assessment returns 400 when windowHours is invalid")
    void shouldReturn400WhenWindowHoursInvalid() throws Exception {
        when(riskAssessmentService.assessSensorRisk(10L, 0))
                .thenThrow(new IllegalArgumentException("windowHours must be a positive integer greater than 0"));

        mockMvc.perform(get("/api/sensors/{sensorId}/risk-assessment", 10L)
                        .param("windowHours", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("windowHours must be a positive integer greater than 0"));
    }

    @Test
    @DisplayName("GET /api/facilities/{facilityId}/risk-assessment returns HTTP 200 with facility aggregation")
    void shouldGetFacilityRiskAssessment() throws Exception {
        when(riskAssessmentService.assessFacilityRisk(1L, 24)).thenReturn(sampleFacilityResponse());

        mockMvc.perform(get("/api/facilities/{facilityId}/risk-assessment", 1L)
                        .param("windowHours", "24")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facilityId").value(1))
                .andExpect(jsonPath("$.facilityName").value("Central Cold Storage"))
                .andExpect(jsonPath("$.totalActiveSensors").value(1))
                .andExpect(jsonPath("$.facilityRiskScore").value(42.50))
                .andExpect(jsonPath("$.facilityRiskTier").value("MODERATE"))
                .andExpect(jsonPath("$.hotspotSensors[0].sensorCode").value("SN-CH-01"));
    }

    @Test
    @DisplayName("GET /api/facilities/{facilityId}/risk-assessment returns 404 when facility not found")
    void shouldReturn404WhenFacilityNotFound() throws Exception {
        when(riskAssessmentService.assessFacilityRisk(999L, 24))
                .thenThrow(new ResourceNotFoundException("Storage facility not found with id: 999"));

        mockMvc.perform(get("/api/facilities/{facilityId}/risk-assessment", 999L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Storage facility not found with id: 999"));
    }

    @Test
    @DisplayName("GET /api/facilities/{facilityId}/risk-assessment returns 400 when facility is inactive")
    void shouldReturn400WhenFacilityIsInactive() throws Exception {
        when(riskAssessmentService.assessFacilityRisk(2L, 24))
                .thenThrow(new IllegalArgumentException("Storage facility is inactive and cannot be assessed for operational risk. Facility ID: 2"));

        mockMvc.perform(get("/api/facilities/{facilityId}/risk-assessment", 2L)
                        .param("windowHours", "24")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Storage facility is inactive and cannot be assessed for operational risk. Facility ID: 2"));
    }
}
