package com.coldchainsentinel.service;

import com.coldchainsentinel.dto.CreateStorageFacilityRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.StorageFacilityResponse;
import com.coldchainsentinel.dto.UpdateStorageFacilityRequest;
import com.coldchainsentinel.exception.ResourceNotFoundException;
import com.coldchainsentinel.model.CommodityProfile;
import com.coldchainsentinel.model.StorageFacility;
import com.coldchainsentinel.repository.CommodityProfileRepository;
import com.coldchainsentinel.repository.StorageFacilityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business service managing storage facilities.
 */
@Service
@Transactional
public class StorageFacilityService {

    private final StorageFacilityRepository facilityRepository;
    private final CommodityProfileRepository commodityProfileRepository;

    public StorageFacilityService(StorageFacilityRepository facilityRepository,
                                  CommodityProfileRepository commodityProfileRepository) {
        this.facilityRepository = facilityRepository;
        this.commodityProfileRepository = commodityProfileRepository;
    }

    /**
     * Create and persist a new storage facility.
     */
    public StorageFacilityResponse createFacility(CreateStorageFacilityRequest request) {
        StorageFacility facility = new StorageFacility(
                request.name(),
                request.location(),
                request.capacityInTonnes(),
                request.facilityType()
        );

        if (request.commodityProfileId() != null) {
            CommodityProfile profile = resolveActiveProfileOrThrow(request.commodityProfileId());
            facility.setCommodityProfile(profile);
        }

        StorageFacility saved = facilityRepository.save(facility);
        return StorageFacilityResponse.fromEntity(saved);
    }

    /**
     * Retrieve paginated list of storage facilities.
     */
    @Transactional(readOnly = true)
    public PagedResponse<StorageFacilityResponse> getAllFacilities(Pageable pageable) {
        Page<StorageFacility> page = facilityRepository.findAll(pageable);
        Page<StorageFacilityResponse> responsePage = page.map(StorageFacilityResponse::fromEntity);
        return PagedResponse.fromPage(responsePage);
    }

    /**
     * Retrieve a storage facility by its ID.
     */
    @Transactional(readOnly = true)
    public StorageFacilityResponse getFacilityById(Long id) {
        StorageFacility facility = findFacilityOrThrow(id);
        return StorageFacilityResponse.fromEntity(facility);
    }

    /**
     * Update an existing storage facility's details.
     */
    public StorageFacilityResponse updateFacility(Long id, UpdateStorageFacilityRequest request) {
        StorageFacility facility = findFacilityOrThrow(id);
        facility.setName(request.name());
        facility.setLocation(request.location());
        facility.setCapacityInTonnes(request.capacityInTonnes());
        facility.setFacilityType(request.facilityType());
        if (request.active() != null) {
            facility.setActive(request.active());
        }

        if (request.commodityProfileId() != null) {
            CommodityProfile profile = resolveActiveProfileOrThrow(request.commodityProfileId());
            facility.setCommodityProfile(profile);
        }

        StorageFacility updated = facilityRepository.save(facility);
        return StorageFacilityResponse.fromEntity(updated);
    }

    /**
     * Assigns a commodity profile to a storage facility.
     */
    public StorageFacilityResponse assignCommodityProfile(Long facilityId, Long profileId) {
        StorageFacility facility = findFacilityOrThrow(facilityId);
        CommodityProfile profile = resolveActiveProfileOrThrow(profileId);
        facility.setCommodityProfile(profile);
        StorageFacility updated = facilityRepository.save(facility);
        return StorageFacilityResponse.fromEntity(updated);
    }

    /**
     * Removes the assigned commodity profile from a storage facility (falls back to global defaults).
     */
    public StorageFacilityResponse removeCommodityProfile(Long facilityId) {
        StorageFacility facility = findFacilityOrThrow(facilityId);
        facility.setCommodityProfile(null);
        StorageFacility updated = facilityRepository.save(facility);
        return StorageFacilityResponse.fromEntity(updated);
    }

    /**
     * Soft-deactivate a storage facility without removing its record.
     */
    public StorageFacilityResponse deactivateFacility(Long id) {
        StorageFacility facility = findFacilityOrThrow(id);
        facility.setActive(false);
        StorageFacility updated = facilityRepository.save(facility);
        return StorageFacilityResponse.fromEntity(updated);
    }

    private StorageFacility findFacilityOrThrow(Long id) {
        return facilityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Storage facility not found with id: " + id));
    }

    private CommodityProfile resolveActiveProfileOrThrow(Long profileId) {
        CommodityProfile profile = commodityProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Commodity profile not found with id: " + profileId));
        if (!profile.isActive()) {
            throw new IllegalArgumentException("Cannot assign inactive commodity profile with id: " + profileId);
        }
        return profile;
    }
}

