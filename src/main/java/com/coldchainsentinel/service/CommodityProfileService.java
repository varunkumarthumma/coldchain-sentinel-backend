package com.coldchainsentinel.service;

import com.coldchainsentinel.dto.CommodityProfileResponse;
import com.coldchainsentinel.dto.CreateCommodityProfileRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.UpdateCommodityProfileRequest;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.CommodityProfile;
import com.coldchainsentinel.repository.CommodityProfileRepository;
import com.coldchainsentinel.repository.StorageFacilityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Business service managing Commodity Profiles.
 */
@Service
@Transactional
public class CommodityProfileService {

    private final CommodityProfileRepository commodityProfileRepository;
    private final StorageFacilityRepository storageFacilityRepository;

    public CommodityProfileService(CommodityProfileRepository commodityProfileRepository,
                                   StorageFacilityRepository storageFacilityRepository) {
        this.commodityProfileRepository = commodityProfileRepository;
        this.storageFacilityRepository = storageFacilityRepository;
    }

    /**
     * Create and persist a new commodity profile.
     */
    public CommodityProfileResponse createProfile(CreateCommodityProfileRequest request) {
        validateThresholdRanges(
                request.targetTemperatureMin(),
                request.targetTemperatureMax(),
                request.targetHumidityMin(),
                request.targetHumidityMax()
        );

        String trimmedName = request.name().trim();
        if (commodityProfileRepository.existsByName(trimmedName)) {
            throw new IllegalArgumentException("Commodity profile with name '" + trimmedName + "' already exists");
        }

        CommodityProfile profile = new CommodityProfile(
                trimmedName,
                request.commodityName().trim(),
                request.variety() != null ? request.variety().trim() : null,
                request.targetTemperatureMin(),
                request.targetTemperatureMax(),
                request.targetHumidityMin(),
                request.targetHumidityMax()
        );

        CommodityProfile saved = commodityProfileRepository.save(profile);
        return CommodityProfileResponse.fromEntity(saved);
    }

    /**
     * Retrieve paginated list of commodity profiles with optional active filter.
     */
    @Transactional(readOnly = true)
    public PagedResponse<CommodityProfileResponse> getAllProfiles(Boolean active, Pageable pageable) {
        Page<CommodityProfile> page;
        if (active != null) {
            page = commodityProfileRepository.findByActive(active, pageable);
        } else {
            page = commodityProfileRepository.findAll(pageable);
        }
        Page<CommodityProfileResponse> responsePage = page.map(CommodityProfileResponse::fromEntity);
        return PagedResponse.fromPage(responsePage);
    }

    /**
     * Retrieve a commodity profile by its ID.
     */
    @Transactional(readOnly = true)
    public CommodityProfileResponse getProfileById(Long id) {
        CommodityProfile profile = findProfileOrThrow(id);
        return CommodityProfileResponse.fromEntity(profile);
    }

    /**
     * Update an existing commodity profile.
     */
    public CommodityProfileResponse updateProfile(Long id, UpdateCommodityProfileRequest request) {
        CommodityProfile profile = findProfileOrThrow(id);

        validateThresholdRanges(
                request.targetTemperatureMin(),
                request.targetTemperatureMax(),
                request.targetHumidityMin(),
                request.targetHumidityMax()
        );

        String trimmedName = request.name().trim();
        if (commodityProfileRepository.existsByNameAndIdNot(trimmedName, id)) {
            throw new IllegalArgumentException("Another commodity profile with name '" + trimmedName + "' already exists");
        }

        profile.setName(trimmedName);
        profile.setCommodityName(request.commodityName().trim());
        profile.setVariety(request.variety() != null ? request.variety().trim() : null);
        profile.setTargetTemperatureMin(request.targetTemperatureMin());
        profile.setTargetTemperatureMax(request.targetTemperatureMax());
        profile.setTargetHumidityMin(request.targetHumidityMin());
        profile.setTargetHumidityMax(request.targetHumidityMax());
        if (request.active() != null) {
            profile.setActive(request.active());
        }

        CommodityProfile updated = commodityProfileRepository.save(profile);
        return CommodityProfileResponse.fromEntity(updated);
    }

    /**
     * Deactivate a commodity profile safely without deleting the database record.
     */
    public CommodityProfileResponse deactivateProfile(Long id) {
        CommodityProfile profile = findProfileOrThrow(id);
        profile.setActive(false);
        CommodityProfile updated = commodityProfileRepository.save(profile);
        return CommodityProfileResponse.fromEntity(updated);
    }

    /**
     * Delete a commodity profile. Fails safely if the profile is actively assigned to any storage facility.
     */
    public void deleteProfile(Long id) {
        CommodityProfile profile = findProfileOrThrow(id);
        if (storageFacilityRepository.existsByCommodityProfileId(id)) {
            throw new IllegalStateException("Cannot delete commodity profile with id " + id + " because it is currently assigned to one or more storage facilities. Deactivate it instead.");
        }
        commodityProfileRepository.delete(profile);
    }

    private CommodityProfile findProfileOrThrow(Long id) {
        return commodityProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commodity profile not found with id: " + id));
    }

    private void validateThresholdRanges(BigDecimal tempMin, BigDecimal tempMax, BigDecimal humidityMin, BigDecimal humidityMax) {
        if (tempMin == null || tempMax == null) {
            throw new IllegalArgumentException("Temperature minimum and maximum cannot be null");
        }
        if (tempMin.compareTo(tempMax) >= 0) {
            throw new IllegalArgumentException(String.format("Minimum temperature (%.2f °C) must be strictly lower than maximum temperature (%.2f °C)", tempMin, tempMax));
        }

        if (humidityMin == null || humidityMax == null) {
            throw new IllegalArgumentException("Humidity minimum and maximum cannot be null");
        }
        if (humidityMin.compareTo(BigDecimal.ZERO) < 0 || humidityMin.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException(String.format("Minimum humidity (%.2f %%) must be between 0.00 %% and 100.00 %%", humidityMin));
        }
        if (humidityMax.compareTo(BigDecimal.ZERO) < 0 || humidityMax.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException(String.format("Maximum humidity (%.2f %%) must be between 0.00 %% and 100.00 %%", humidityMax));
        }
        if (humidityMin.compareTo(humidityMax) >= 0) {
            throw new IllegalArgumentException(String.format("Minimum humidity (%.2f %%) must be strictly lower than maximum humidity (%.2f %%)", humidityMin, humidityMax));
        }
    }
}
