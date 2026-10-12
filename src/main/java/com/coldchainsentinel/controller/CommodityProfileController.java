package com.coldchainsentinel.controller;

import com.coldchainsentinel.dto.CommodityProfileResponse;
import com.coldchainsentinel.dto.CreateCommodityProfileRequest;
import com.coldchainsentinel.dto.PagedResponse;
import com.coldchainsentinel.dto.UpdateCommodityProfileRequest;
import com.coldchainsentinel.service.CommodityProfileService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing endpoints for Commodity Profile management.
 */
@RestController
@RequestMapping("/api/commodity-profiles")
public class CommodityProfileController {

    private final CommodityProfileService commodityProfileService;

    public CommodityProfileController(CommodityProfileService commodityProfileService) {
        this.commodityProfileService = commodityProfileService;
    }

    /**
     * Create a new commodity profile.
     */
    @PostMapping
    public ResponseEntity<CommodityProfileResponse> createProfile(
            @Valid @RequestBody CreateCommodityProfileRequest request
    ) {
        CommodityProfileResponse response = commodityProfileService.createProfile(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all commodity profiles with pagination and optional active status filter.
     */
    @GetMapping
    public ResponseEntity<PagedResponse<CommodityProfileResponse>> getAllProfiles(
            @RequestParam(required = false) Boolean active,
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PagedResponse<CommodityProfileResponse> response = commodityProfileService.getAllProfiles(active, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve a specific commodity profile by its ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CommodityProfileResponse> getProfileById(@PathVariable Long id) {
        CommodityProfileResponse response = commodityProfileService.getProfileById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Update an existing commodity profile.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CommodityProfileResponse> updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCommodityProfileRequest request
    ) {
        CommodityProfileResponse response = commodityProfileService.updateProfile(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Safely deactivate a commodity profile without deleting its database record.
     */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<CommodityProfileResponse> deactivateProfile(@PathVariable Long id) {
        CommodityProfileResponse response = commodityProfileService.deactivateProfile(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a commodity profile if not referenced by any facility.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProfile(@PathVariable Long id) {
        commodityProfileService.deleteProfile(id);
        return ResponseEntity.noContent().build();
    }
}
