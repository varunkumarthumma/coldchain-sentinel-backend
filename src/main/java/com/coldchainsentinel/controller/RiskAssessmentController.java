package com.coldchainsentinel.controller;

import com.coldchainsentinel.dto.FacilityRiskAssessmentResponse;
import com.coldchainsentinel.dto.SensorRiskAssessmentResponse;
import com.coldchainsentinel.service.RiskAssessmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Post-Harvest Loss Risk Assessments across sensors and storage facilities.
 */
@RestController
@RequestMapping("/api")
public class RiskAssessmentController {

    private final RiskAssessmentService riskAssessmentService;

    public RiskAssessmentController(RiskAssessmentService riskAssessmentService) {
        this.riskAssessmentService = riskAssessmentService;
    }

    /**
     * Assesses cold-chain risk for an individual sensor over a requested observation window.
     */
    @GetMapping("/sensors/{sensorId}/risk-assessment")
    public ResponseEntity<SensorRiskAssessmentResponse> getSensorRiskAssessment(
            @PathVariable Long sensorId,
            @RequestParam(defaultValue = "24") int windowHours
    ) {
        SensorRiskAssessmentResponse response = riskAssessmentService.assessSensorRisk(sensorId, windowHours);
        return ResponseEntity.ok(response);
    }

    /**
     * Assesses facility-wide cold-chain risk aggregating across all active sensors in the facility.
     */
    @GetMapping("/facilities/{facilityId}/risk-assessment")
    public ResponseEntity<FacilityRiskAssessmentResponse> getFacilityRiskAssessment(
            @PathVariable Long facilityId,
            @RequestParam(defaultValue = "24") int windowHours
    ) {
        FacilityRiskAssessmentResponse response = riskAssessmentService.assessFacilityRisk(facilityId, windowHours);
        return ResponseEntity.ok(response);
    }
}
