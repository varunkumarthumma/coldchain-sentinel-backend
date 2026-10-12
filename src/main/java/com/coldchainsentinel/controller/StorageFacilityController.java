package com.coldchainsentinel.controller;

import com.coldchainsentinel.dto.CreateStorageFacilityRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.StorageFacilityResponse;
import com.coldchainsentinel.dto.UpdateStorageFacilityRequest;
import com.coldchainsentinel.service.StorageFacilityService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing endpoints for Storage Facility management.
 */
@RestController
@RequestMapping("/api/facilities")
public class StorageFacilityController {

    private final StorageFacilityService facilityService;

    public StorageFacilityController(StorageFacilityService facilityService) {
        this.facilityService = facilityService;
    }

    /**
     * Create a new storage facility.
     */
    @PostMapping
    public ResponseEntity<StorageFacilityResponse> createFacility(
            @Valid @RequestBody CreateStorageFacilityRequest request
    ) {
        StorageFacilityResponse response = facilityService.createFacility(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all storage facilities with pagination support.
     */
    @GetMapping
    public ResponseEntity<PagedResponse<StorageFacilityResponse>> getAllFacilities(
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PagedResponse<StorageFacilityResponse> response = facilityService.getAllFacilities(pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve a specific storage facility by its ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<StorageFacilityResponse> getFacilityById(@PathVariable Long id) {
        StorageFacilityResponse response = facilityService.getFacilityById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Update an existing storage facility.
     */
    @PutMapping("/{id}")
    public ResponseEntity<StorageFacilityResponse> updateFacility(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStorageFacilityRequest request
    ) {
        StorageFacilityResponse response = facilityService.updateFacility(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Soft-deactivate a storage facility without removing its database record.
     */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<StorageFacilityResponse> deactivateFacility(@PathVariable Long id) {
        StorageFacilityResponse response = facilityService.deactivateFacility(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Assign a commodity profile to a storage facility.
     */
    @PutMapping("/{id}/commodity-profile/{profileId}")
    public ResponseEntity<StorageFacilityResponse> assignCommodityProfile(
            @PathVariable Long id,
            @PathVariable Long profileId
    ) {
        StorageFacilityResponse response = facilityService.assignCommodityProfile(id, profileId);
        return ResponseEntity.ok(response);
    }

    /**
     * Remove the assigned commodity profile from a storage facility (reverts to global defaults).
     */
    @DeleteMapping("/{id}/commodity-profile")
    public ResponseEntity<StorageFacilityResponse> removeCommodityProfile(@PathVariable Long id) {
        StorageFacilityResponse response = facilityService.removeCommodityProfile(id);
        return ResponseEntity.ok(response);
    }
}

