package com.coldchainsentinel.service;

import com.coldchainsentinel.dto.CommodityProfileResponse;
import com.coldchainsentinel.dto.CreateCommodityProfileRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.UpdateCommodityProfileRequest;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.CommodityProfile;
import com.coldchainsentinel.repository.CommodityProfileRepository;
import com.coldchainsentinel.repository.StorageFacilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommodityProfileServiceTest {

    @Mock
    private CommodityProfileRepository profileRepository;

    @Mock
    private StorageFacilityRepository facilityRepository;

    @InjectMocks
    private CommodityProfileService profileService;

    private CommodityProfile sampleProfile;

    @BeforeEach
    void setUp() {
        sampleProfile = new CommodityProfile(
                "Apples Standard",
                "Apples",
                "Fuji",
                new BigDecimal("0.50"),
                new BigDecimal("2.50"),
                new BigDecimal("85.00"),
                new BigDecimal("95.00")
        );
        sampleProfile.setId(1L);
    }

    @Test
    @DisplayName("Successfully creates a valid commodity profile")
    void createProfile_success() {
        CreateCommodityProfileRequest request = new CreateCommodityProfileRequest(
                "Apples Standard",
                "Apples",
                "Fuji",
                new BigDecimal("0.50"),
                new BigDecimal("2.50"),
                new BigDecimal("85.00"),
                new BigDecimal("95.00")
        );

        when(profileRepository.existsByName("Apples Standard")).thenReturn(false);
        when(profileRepository.save(any(CommodityProfile.class))).thenAnswer(invocation -> {
            CommodityProfile saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        CommodityProfileResponse response = profileService.createProfile(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Apples Standard");
        assertThat(response.commodityName()).isEqualTo("Apples");
        assertThat(response.variety()).isEqualTo("Fuji");
        assertThat(response.targetTemperatureMin()).isEqualByComparingTo("0.50");
        assertThat(response.targetTemperatureMax()).isEqualByComparingTo("2.50");
        assertThat(response.targetHumidityMin()).isEqualByComparingTo("85.00");
        assertThat(response.targetHumidityMax()).isEqualByComparingTo("95.00");
        assertThat(response.active()).isTrue();
    }

    @Test
    @DisplayName("Rejects duplicate profile name upon creation")
    void createProfile_duplicateName() {
        CreateCommodityProfileRequest request = new CreateCommodityProfileRequest(
                "Apples Standard",
                "Apples",
                "Fuji",
                new BigDecimal("0.50"),
                new BigDecimal("2.50"),
                new BigDecimal("85.00"),
                new BigDecimal("95.00")
        );

        when(profileRepository.existsByName("Apples Standard")).thenReturn(true);

        assertThatThrownBy(() -> profileService.createProfile(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Rejects temperature min greater than or equal to temperature max")
    void createProfile_invalidTempRange() {
        CreateCommodityProfileRequest request = new CreateCommodityProfileRequest(
                "Invalid Temp",
                "Apples",
                null,
                new BigDecimal("5.00"),
                new BigDecimal("2.00"),
                new BigDecimal("80.00"),
                new BigDecimal("90.00")
        );

        assertThatThrownBy(() -> profileService.createProfile(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minimum temperature (5.00 °C) must be strictly lower than maximum temperature (2.00 °C)");
    }

    @Test
    @DisplayName("Rejects humidity min greater than or equal to humidity max")
    void createProfile_invalidHumidityRange() {
        CreateCommodityProfileRequest request = new CreateCommodityProfileRequest(
                "Invalid Humidity",
                "Apples",
                null,
                new BigDecimal("1.00"),
                new BigDecimal("4.00"),
                new BigDecimal("95.00"),
                new BigDecimal("90.00")
        );

        assertThatThrownBy(() -> profileService.createProfile(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minimum humidity (95.00 %) must be strictly lower than maximum humidity (90.00 %)");
    }

    @Test
    @DisplayName("Rejects out of bounds humidity (< 0 or > 100)")
    void createProfile_outOfBoundsHumidity() {
        CreateCommodityProfileRequest request = new CreateCommodityProfileRequest(
                "Negative Humidity",
                "Apples",
                null,
                new BigDecimal("1.00"),
                new BigDecimal("4.00"),
                new BigDecimal("-5.00"),
                new BigDecimal("90.00")
        );

        assertThatThrownBy(() -> profileService.createProfile(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be between 0.00 % and 100.00 %");
    }

    @Test
    @DisplayName("Retrieves profile by ID")
    void getProfileById_found() {
        when(profileRepository.findById(1L)).thenReturn(Optional.of(sampleProfile));

        CommodityProfileResponse response = profileService.getProfileById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Apples Standard");
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when profile ID not found")
    void getProfileById_notFound() {
        when(profileRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getProfileById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Commodity profile not found with id: 999");
    }

    @Test
    @DisplayName("Retrieves paginated list of profiles")
    void getAllProfiles_paginated() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        when(profileRepository.findAll(pageRequest)).thenReturn(new PageImpl<>(List.of(sampleProfile)));

        PagedResponse<CommodityProfileResponse> result = profileService.getAllProfiles(null, pageRequest);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).name()).isEqualTo("Apples Standard");
    }

    @Test
    @DisplayName("Updates profile details successfully")
    void updateProfile_success() {
        UpdateCommodityProfileRequest request = new UpdateCommodityProfileRequest(
                "Apples Premium",
                "Apples",
                "Honeycrisp",
                new BigDecimal("1.00"),
                new BigDecimal("3.00"),
                new BigDecimal("88.00"),
                new BigDecimal("96.00"),
                true
        );

        when(profileRepository.findById(1L)).thenReturn(Optional.of(sampleProfile));
        when(profileRepository.existsByNameAndIdNot("Apples Premium", 1L)).thenReturn(false);
        when(profileRepository.save(sampleProfile)).thenReturn(sampleProfile);

        CommodityProfileResponse updated = profileService.updateProfile(1L, request);

        assertThat(updated.name()).isEqualTo("Apples Premium");
        assertThat(updated.variety()).isEqualTo("Honeycrisp");
        assertThat(updated.targetTemperatureMin()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("Deactivates profile safely")
    void deactivateProfile_success() {
        when(profileRepository.findById(1L)).thenReturn(Optional.of(sampleProfile));
        when(profileRepository.save(sampleProfile)).thenReturn(sampleProfile);

        CommodityProfileResponse deactivated = profileService.deactivateProfile(1L);

        assertThat(deactivated.active()).isFalse();
        assertThat(sampleProfile.isActive()).isFalse();
    }

    @Test
    @DisplayName("Deletes unreferenced profile successfully")
    void deleteProfile_unreferenced() {
        when(profileRepository.findById(1L)).thenReturn(Optional.of(sampleProfile));
        when(facilityRepository.existsByCommodityProfileId(1L)).thenReturn(false);

        profileService.deleteProfile(1L);

        verify(profileRepository).delete(sampleProfile);
    }

    @Test
    @DisplayName("Prevents deletion of profile when referenced by a storage facility")
    void deleteProfile_referenced_throwsIllegalStateException() {
        when(profileRepository.findById(1L)).thenReturn(Optional.of(sampleProfile));
        when(facilityRepository.existsByCommodityProfileId(1L)).thenReturn(true);

        assertThatThrownBy(() -> profileService.deleteProfile(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot delete commodity profile with id 1 because it is currently assigned to one or more storage facilities");

        verify(profileRepository, never()).delete(any());
    }
}
