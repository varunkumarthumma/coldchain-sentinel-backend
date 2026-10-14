package com.coldchainsentinel.controller;

import com.coldchainsentinel.dto.CommodityProfileResponse;
import com.coldchainsentinel.dto.CreateCommodityProfileRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.UpdateCommodityProfileRequest;
import com.coldchainsentinel.exception.GlobalExceptionHandler;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.service.CommodityProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@WebMvcTest(CommodityProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CommodityProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommodityProfileService profileService;

    private CommodityProfileResponse createSampleResponse(Long id, String name, boolean active) {
        return new CommodityProfileResponse(
                id,
                name,
                "Apples",
                "Gala",
                new BigDecimal("0.50"),
                new BigDecimal("2.50"),
                new BigDecimal("85.00"),
                new BigDecimal("95.00"),
                active,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("POST /api/commodity-profiles - 201 Created on valid request")
    void createProfile_success() throws Exception {
        CreateCommodityProfileRequest request = new CreateCommodityProfileRequest(
                "Apples Cold Spec",
                "Apples",
                "Gala",
                new BigDecimal("0.50"),
                new BigDecimal("2.50"),
                new BigDecimal("85.00"),
                new BigDecimal("95.00")
        );

        when(profileService.createProfile(any())).thenReturn(createSampleResponse(1L, "Apples Cold Spec", true));

        mockMvc.perform(post("/api/commodity-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Apples Cold Spec"))
                .andExpect(jsonPath("$.commodityName").value("Apples"))
                .andExpect(jsonPath("$.targetTemperatureMin").value(0.50))
                .andExpect(jsonPath("$.targetTemperatureMax").value(2.50))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /api/commodity-profiles - 400 Bad Request on blank name")
    void createProfile_blankName_fails() throws Exception {
        CreateCommodityProfileRequest request = new CreateCommodityProfileRequest(
                " ",
                "Apples",
                null,
                new BigDecimal("0.50"),
                new BigDecimal("2.50"),
                new BigDecimal("85.00"),
                new BigDecimal("95.00")
        );

        mockMvc.perform(post("/api/commodity-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }


    @Test
    @DisplayName("POST /api/commodity-profiles - 400 Bad Request on invalid numeric range from service")
    void createProfile_serviceValidationException_returns400() throws Exception {
        CreateCommodityProfileRequest request = new CreateCommodityProfileRequest(
                "Invalid Profile",
                "Apples",
                null,
                new BigDecimal("5.00"),
                new BigDecimal("2.00"),
                new BigDecimal("85.00"),
                new BigDecimal("95.00")
        );

        when(profileService.createProfile(any()))
                .thenThrow(new IllegalArgumentException("Minimum temperature (5.00 °C) must be strictly lower than maximum temperature (2.00 °C)"));

        mockMvc.perform(post("/api/commodity-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Minimum temperature (5.00 °C) must be strictly lower than maximum temperature (2.00 °C)"));
    }

    @Test
    @DisplayName("GET /api/commodity-profiles - 200 OK returns paged list")
    void getAllProfiles_success() throws Exception {
        PagedResponse<CommodityProfileResponse> pagedResponse = new PagedResponse<>(
                List.of(createSampleResponse(1L, "Apples Spec", true)),
                0,
                10,
                1,
                1,
                true,
                true
        );

        when(profileService.getAllProfiles(eq(null), any(Pageable.class))).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/commodity-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Apples Spec"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/commodity-profiles/{id} - 200 OK returns profile")
    void getProfileById_success() throws Exception {
        when(profileService.getProfileById(1L)).thenReturn(createSampleResponse(1L, "Apples Spec", true));

        mockMvc.perform(get("/api/commodity-profiles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Apples Spec"));
    }

    @Test
    @DisplayName("GET /api/commodity-profiles/{id} - 404 Not Found")
    void getProfileById_notFound() throws Exception {
        when(profileService.getProfileById(99L)).thenThrow(new ResourceNotFoundException("Commodity profile not found with id: 99"));

        mockMvc.perform(get("/api/commodity-profiles/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PUT /api/commodity-profiles/{id} - 200 OK on update")
    void updateProfile_success() throws Exception {
        UpdateCommodityProfileRequest request = new UpdateCommodityProfileRequest(
                "Apples Updated",
                "Apples",
                "Fuji",
                new BigDecimal("1.00"),
                new BigDecimal("3.00"),
                new BigDecimal("88.00"),
                new BigDecimal("94.00"),
                true
        );

        when(profileService.updateProfile(eq(1L), any())).thenReturn(createSampleResponse(1L, "Apples Updated", true));

        mockMvc.perform(put("/api/commodity-profiles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Apples Updated"));
    }

    @Test
    @DisplayName("PATCH /api/commodity-profiles/{id}/deactivate - 200 OK deactivates profile")
    void deactivateProfile_success() throws Exception {
        when(profileService.deactivateProfile(1L)).thenReturn(createSampleResponse(1L, "Apples Spec", false));

        mockMvc.perform(patch("/api/commodity-profiles/1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("DELETE /api/commodity-profiles/{id} - 204 No Content on unreferenced profile")
    void deleteProfile_success() throws Exception {
        doNothing().when(profileService).deleteProfile(1L);

        mockMvc.perform(delete("/api/commodity-profiles/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/commodity-profiles/{id} - 400 Bad Request when referenced by facility")
    void deleteProfile_referenced_throws400() throws Exception {
        doThrow(new IllegalStateException("Cannot delete commodity profile with id 1 because it is currently assigned to one or more storage facilities"))
                .when(profileService).deleteProfile(1L);

        mockMvc.perform(delete("/api/commodity-profiles/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot delete commodity profile with id 1 because it is currently assigned to one or more storage facilities"));
    }
}
