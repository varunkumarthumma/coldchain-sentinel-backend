package com.coldchainsentinel.controller;

import com.coldchainsentinel.ColdChainSentinelApplication;
import com.coldchainsentinel.dto.CreateStorageFacilityRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.StorageFacilityResponse;
import com.coldchainsentinel.dto.UpdateStorageFacilityRequest;
import com.coldchainsentinel.exception.GlobalExceptionHandler;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.FacilityType;
import com.coldchainsentinel.service.StorageFacilityService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@WebMvcTest(StorageFacilityController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {ColdChainSentinelApplication.class, StorageFacilityController.class, GlobalExceptionHandler.class})
class StorageFacilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StorageFacilityService facilityService;

    private StorageFacilityResponse sampleResponse() {
        return new StorageFacilityResponse(
                1L,
                "Central Hub",
                "Sector 4, Hyderabad",
                new BigDecimal("500.00"),
                FacilityType.COLD_STORAGE,
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("POST /api/facilities creates facility and returns 201 Created")
    void shouldCreateFacility() throws Exception {
        CreateStorageFacilityRequest request = new CreateStorageFacilityRequest(
                "Central Hub",
                "Sector 4, Hyderabad",
                new BigDecimal("500.00"),
                FacilityType.COLD_STORAGE
        );

        when(facilityService.createFacility(any(CreateStorageFacilityRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/facilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Central Hub"))
                .andExpect(jsonPath("$.capacityInTonnes").value(500.00))
                .andExpect(jsonPath("$.facilityType").value("COLD_STORAGE"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /api/facilities returns 400 Bad Request on validation failure")
    void shouldReturnBadRequestWhenInputInvalid() throws Exception {
        CreateStorageFacilityRequest invalidRequest = new CreateStorageFacilityRequest(
                "",
                "",
                new BigDecimal("-5.00"),
                null
        );

        mockMvc.perform(post("/api/facilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @DisplayName("GET /api/facilities returns 200 OK and paginated response")
    void shouldGetAllFacilities() throws Exception {
        PagedResponse<StorageFacilityResponse> pagedResponse = new PagedResponse<>(
                List.of(sampleResponse()),
                0,
                10,
                1,
                1,
                true,
                true
        );

        when(facilityService.getAllFacilities(any())).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/facilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("Central Hub"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @DisplayName("GET /api/facilities/{id} returns 200 OK when facility exists")
    void shouldGetFacilityById() throws Exception {
        when(facilityService.getFacilityById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/facilities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Central Hub"));
    }

    @Test
    @DisplayName("GET /api/facilities/{id} returns 404 Not Found when facility does not exist")
    void shouldReturn404WhenFacilityNotFound() throws Exception {
        when(facilityService.getFacilityById(99L)).thenThrow(new ResourceNotFoundException("Storage facility not found with id: 99"));

        mockMvc.perform(get("/api/facilities/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Storage facility not found with id: 99"));
    }

    @Test
    @DisplayName("PUT /api/facilities/{id} updates and returns 200 OK")
    void shouldUpdateFacility() throws Exception {
        UpdateStorageFacilityRequest request = new UpdateStorageFacilityRequest(
                "Central Hub Updated",
                "Sector 4, Hyderabad",
                new BigDecimal("600.00"),
                FacilityType.COLD_STORAGE,
                true
        );

        when(facilityService.updateFacility(eq(1L), any(UpdateStorageFacilityRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/facilities/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/facilities/{id}/deactivate returns 200 OK")
    void shouldDeactivateFacility() throws Exception {
        StorageFacilityResponse deactivated = new StorageFacilityResponse(
                1L,
                "Central Hub",
                "Sector 4, Hyderabad",
                new BigDecimal("500.00"),
                FacilityType.COLD_STORAGE,
                false,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(facilityService.deactivateFacility(1L)).thenReturn(deactivated);

        mockMvc.perform(patch("/api/facilities/1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("PUT /api/facilities/{id}/commodity-profile/{profileId} assigns profile and returns 200 OK")
    void shouldAssignCommodityProfile() throws Exception {
        StorageFacilityResponse assigned = new StorageFacilityResponse(
                1L,
                "Central Hub",
                "Sector 4, Hyderabad",
                new BigDecimal("500.00"),
                FacilityType.COLD_STORAGE,
                true,
                5L,
                "Apples Standard",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(facilityService.assignCommodityProfile(1L, 5L)).thenReturn(assigned);

        mockMvc.perform(put("/api/facilities/1/commodity-profile/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commodityProfileId").value(5))
                .andExpect(jsonPath("$.commodityProfileName").value("Apples Standard"));
    }

    @Test
    @DisplayName("DELETE /api/facilities/{id}/commodity-profile removes profile and returns 200 OK")
    void shouldRemoveCommodityProfile() throws Exception {
        StorageFacilityResponse unassigned = new StorageFacilityResponse(
                1L,
                "Central Hub",
                "Sector 4, Hyderabad",
                new BigDecimal("500.00"),
                FacilityType.COLD_STORAGE,
                true,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(facilityService.removeCommodityProfile(1L)).thenReturn(unassigned);

        mockMvc.perform(delete("/api/facilities/1/commodity-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commodityProfileId").doesNotExist());
    }
}

