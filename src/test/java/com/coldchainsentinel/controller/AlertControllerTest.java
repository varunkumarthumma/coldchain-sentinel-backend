package com.coldchainsentinel.controller;

import com.coldchainsentinel.ColdChainSentinelApplication;
import com.coldchainsentinel.dto.AlertResponse;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.exception.GlobalExceptionHandler;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.AlertSeverity;
import com.coldchainsentinel.model.AlertStatus;
import com.coldchainsentinel.model.AlertType;
import com.coldchainsentinel.service.AlertService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertController.class)
@ContextConfiguration(classes = {ColdChainSentinelApplication.class, AlertController.class, GlobalExceptionHandler.class})
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertService alertService;

    private AlertResponse sampleAlertResponse() {
        return new AlertResponse(
                1L,
                10L,
                "SN-TEMP-01",
                100L,
                "Central Cold Hub",
                AlertType.HIGH_TEMPERATURE,
                AlertSeverity.CRITICAL,
                AlertStatus.OPEN,
                "High temperature alert: 12.50 °C exceeds limit of 8.00 °C",
                new BigDecimal("12.50"),
                new BigDecimal("8.00"),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null
        );
    }

    @Test
    @DisplayName("GET /api/alerts returns paginated alerts with HTTP 200")
    void shouldGetAlertsPaginated() throws Exception {
        PagedResponse<AlertResponse> pagedResponse = new PagedResponse<>(
                List.of(sampleAlertResponse()),
                0,
                20,
                1L,
                1,
                true,
                true
        );

        when(alertService.getAlerts(any(), any(), any(Pageable.class))).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/alerts")
                        .param("page", "0")
                        .param("size", "20")
                        .param("status", "OPEN")
                        .param("severity", "CRITICAL")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].alertType").value("HIGH_TEMPERATURE"))
                .andExpect(jsonPath("$.content[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/alerts/{id} returns alert details with HTTP 200")
    void shouldGetAlertById() throws Exception {
        when(alertService.getAlertById(1L)).thenReturn(sampleAlertResponse());

        mockMvc.perform(get("/api/alerts/{id}", 1L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sensorCode").value("SN-TEMP-01"))
                .andExpect(jsonPath("$.facilityName").value("Central Cold Hub"));
    }

    @Test
    @DisplayName("GET /api/alerts/{id} returns 404 when alert does not exist")
    void shouldReturn404WhenAlertNotFound() throws Exception {
        when(alertService.getAlertById(999L))
                .thenThrow(new ResourceNotFoundException("Alert not found with id: 999"));

        mockMvc.perform(get("/api/alerts/{id}", 999L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Alert not found with id: 999"));
    }

    @Test
    @DisplayName("GET /api/sensors/{sensorId}/alerts returns sensor's alerts with HTTP 200")
    void shouldGetAlertsBySensor() throws Exception {
        when(alertService.getAlertsBySensor(10L)).thenReturn(List.of(sampleAlertResponse()));

        mockMvc.perform(get("/api/sensors/{sensorId}/alerts", 10L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].sensorId").value(10));
    }

    @Test
    @DisplayName("GET /api/sensors/{sensorId}/alerts returns 404 when sensor not found")
    void shouldReturn404WhenSensorNotFoundForAlerts() throws Exception {
        when(alertService.getAlertsBySensor(999L))
                .thenThrow(new ResourceNotFoundException("Sensor not found with id: 999"));

        mockMvc.perform(get("/api/sensors/{sensorId}/alerts", 999L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Sensor not found with id: 999"));
    }

    @Test
    @DisplayName("PATCH /api/alerts/{id}/acknowledge transitions to ACKNOWLEDGED with HTTP 200")
    void shouldAcknowledgeAlert() throws Exception {
        AlertResponse ackResponse = new AlertResponse(
                1L, 10L, "SN-TEMP-01", 100L, "Central Cold Hub",
                AlertType.HIGH_TEMPERATURE, AlertSeverity.CRITICAL, AlertStatus.ACKNOWLEDGED,
                "High temperature alert", new BigDecimal("12.50"), new BigDecimal("8.00"),
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null
        );

        when(alertService.acknowledgeAlert(1L)).thenReturn(ackResponse);

        mockMvc.perform(patch("/api/alerts/{id}/acknowledge", 1L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").isNotEmpty());
    }

    @Test
    @DisplayName("PATCH /api/alerts/{id}/acknowledge returns HTTP 400 on invalid transition")
    void shouldReturn400OnInvalidAcknowledgeTransition() throws Exception {
        when(alertService.acknowledgeAlert(1L))
                .thenThrow(new IllegalArgumentException("Cannot acknowledge alert with status: ACKNOWLEDGED. Only OPEN alerts can be acknowledged."));

        mockMvc.perform(patch("/api/alerts/{id}/acknowledge", 1L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot acknowledge alert with status: ACKNOWLEDGED. Only OPEN alerts can be acknowledged."));
    }

    @Test
    @DisplayName("PATCH /api/alerts/{id}/resolve transitions to RESOLVED with HTTP 200")
    void shouldResolveAlert() throws Exception {
        AlertResponse resResponse = new AlertResponse(
                1L, 10L, "SN-TEMP-01", 100L, "Central Cold Hub",
                AlertType.HIGH_TEMPERATURE, AlertSeverity.CRITICAL, AlertStatus.RESOLVED,
                "High temperature alert", new BigDecimal("12.50"), new BigDecimal("8.00"),
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()
        );

        when(alertService.resolveAlert(1L)).thenReturn(resResponse);

        mockMvc.perform(patch("/api/alerts/{id}/resolve", 1L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
    }

    @Test
    @DisplayName("PATCH /api/alerts/{id}/resolve returns HTTP 400 when alert already resolved")
    void shouldReturn400OnAlreadyResolvedAlert() throws Exception {
        when(alertService.resolveAlert(1L))
                .thenThrow(new IllegalArgumentException("Alert is already resolved."));

        mockMvc.perform(patch("/api/alerts/{id}/resolve", 1L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Alert is already resolved."));
    }
}
