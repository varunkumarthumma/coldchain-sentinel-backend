package com.coldchainsentinel.service;

import com.coldchainsentinel.dto.CreateStorageFacilityRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.StorageFacilityResponse;
import com.coldchainsentinel.dto.UpdateStorageFacilityRequest;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.FacilityType;
import com.coldchainsentinel.model.StorageFacility;
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
class StorageFacilityServiceTest {

    @Mock
    private StorageFacilityRepository facilityRepository;

    @Mock
    private com.coldchainsentinel.repository.CommodityProfileRepository commodityProfileRepository;

    @InjectMocks
    private StorageFacilityService facilityService;


    private StorageFacility sampleFacility;

    @BeforeEach
    void setUp() {
        sampleFacility = new StorageFacility(
                "Central Cold Hub",
                "Warehouse District 4, Hyderabad",
                new BigDecimal("500.00"),
                FacilityType.COLD_STORAGE
        );
        sampleFacility.setId(1L);
        sampleFacility.setActive(true);
        sampleFacility.setCreatedAt(LocalDateTime.now());
        sampleFacility.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("createFacility successfully saves and returns response DTO")
    void shouldCreateFacility() {
        CreateStorageFacilityRequest request = new CreateStorageFacilityRequest(
                "Central Cold Hub",
                "Warehouse District 4, Hyderabad",
                new BigDecimal("500.00"),
                FacilityType.COLD_STORAGE
        );

        when(facilityRepository.save(any(StorageFacility.class))).thenReturn(sampleFacility);

        StorageFacilityResponse response = facilityService.createFacility(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Central Cold Hub");
        assertThat(response.capacityInTonnes()).isEqualTo(new BigDecimal("500.00"));
        assertThat(response.facilityType()).isEqualTo(FacilityType.COLD_STORAGE);
        assertThat(response.active()).isTrue();

        verify(facilityRepository, times(1)).save(any(StorageFacility.class));
    }

    @Test
    @DisplayName("getFacilityById returns response when facility exists")
    void shouldGetFacilityById() {
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(sampleFacility));

        StorageFacilityResponse response = facilityService.getFacilityById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Central Cold Hub");
    }

    @Test
    @DisplayName("getFacilityById throws ResourceNotFoundException when facility does not exist")
    void shouldThrowWhenFacilityNotFound() {
        when(facilityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facilityService.getFacilityById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("updateFacility modifies existing record and returns updated response")
    void shouldUpdateFacility() {
        UpdateStorageFacilityRequest request = new UpdateStorageFacilityRequest(
                "Updated North Cold Hub",
                "Sector 7, Bengaluru",
                new BigDecimal("750.00"),
                FacilityType.CONTROLLED_ATMOSPHERE,
                true
        );

        when(facilityRepository.findById(1L)).thenReturn(Optional.of(sampleFacility));
        when(facilityRepository.save(any(StorageFacility.class))).thenReturn(sampleFacility);

        StorageFacilityResponse response = facilityService.updateFacility(1L, request);

        assertThat(response).isNotNull();
        verify(facilityRepository).save(sampleFacility);
        assertThat(sampleFacility.getName()).isEqualTo("Updated North Cold Hub");
        assertThat(sampleFacility.getCapacityInTonnes()).isEqualTo(new BigDecimal("750.00"));
        assertThat(sampleFacility.getFacilityType()).isEqualTo(FacilityType.CONTROLLED_ATMOSPHERE);
    }

    @Test
    @DisplayName("deactivateFacility sets active to false without deleting the record")
    void shouldDeactivateFacility() {
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(sampleFacility));
        when(facilityRepository.save(any(StorageFacility.class))).thenReturn(sampleFacility);

        StorageFacilityResponse response = facilityService.deactivateFacility(1L);

        assertThat(response).isNotNull();
        assertThat(sampleFacility.isActive()).isFalse();
        verify(facilityRepository).save(sampleFacility);
        verify(facilityRepository, never()).delete(any());
        verify(facilityRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("getAllFacilities returns paginated response")
    void shouldGetAllFacilities() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<StorageFacility> facilityPage = new PageImpl<>(List.of(sampleFacility), pageable, 1);

        when(facilityRepository.findAll(pageable)).thenReturn(facilityPage);

        PagedResponse<StorageFacilityResponse> response = facilityService.getAllFacilities(pageable);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.page()).isEqualTo(0);
    }

    @Test
    @DisplayName("assignCommodityProfile assigns active profile to facility")
    void shouldAssignActiveCommodityProfile() {
        com.coldchainsentinel.model.CommodityProfile profile = new com.coldchainsentinel.model.CommodityProfile(
                "Apples Profile", "Apples", null,
                new BigDecimal("1.00"), new BigDecimal("3.00"),
                new BigDecimal("85.00"), new BigDecimal("95.00")
        );
        profile.setId(5L);
        profile.setActive(true);

        when(facilityRepository.findById(1L)).thenReturn(Optional.of(sampleFacility));
        when(commodityProfileRepository.findById(5L)).thenReturn(Optional.of(profile));
        when(facilityRepository.save(any(StorageFacility.class))).thenReturn(sampleFacility);

        StorageFacilityResponse response = facilityService.assignCommodityProfile(1L, 5L);

        assertThat(response).isNotNull();
        assertThat(sampleFacility.getCommodityProfile()).isEqualTo(profile);
        verify(facilityRepository).save(sampleFacility);
    }

    @Test
    @DisplayName("assignCommodityProfile rejects inactive profile")
    void shouldRejectAssigningInactiveProfile() {
        com.coldchainsentinel.model.CommodityProfile inactiveProfile = new com.coldchainsentinel.model.CommodityProfile(
                "Inactive Profile", "Apples", null,
                new BigDecimal("1.00"), new BigDecimal("3.00"),
                new BigDecimal("85.00"), new BigDecimal("95.00")
        );
        inactiveProfile.setId(5L);
        inactiveProfile.setActive(false);

        when(facilityRepository.findById(1L)).thenReturn(Optional.of(sampleFacility));
        when(commodityProfileRepository.findById(5L)).thenReturn(Optional.of(inactiveProfile));

        assertThatThrownBy(() -> facilityService.assignCommodityProfile(1L, 5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot assign inactive commodity profile");
    }

    @Test
    @DisplayName("removeCommodityProfile clears assigned profile from facility")
    void shouldRemoveCommodityProfile() {
        com.coldchainsentinel.model.CommodityProfile profile = new com.coldchainsentinel.model.CommodityProfile();
        sampleFacility.setCommodityProfile(profile);

        when(facilityRepository.findById(1L)).thenReturn(Optional.of(sampleFacility));
        when(facilityRepository.save(any(StorageFacility.class))).thenReturn(sampleFacility);

        StorageFacilityResponse response = facilityService.removeCommodityProfile(1L);

        assertThat(response).isNotNull();
        assertThat(sampleFacility.getCommodityProfile()).isNull();
        verify(facilityRepository).save(sampleFacility);
    }
}

