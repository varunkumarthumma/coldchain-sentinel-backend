package com.coldchainsentinel.dto;

import com.coldchainsentinel.model.RiskTier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of a Post-Harvest Loss Risk Assessment evaluation.
 */
public record RiskAssessmentResult(
        boolean sufficientData,
        BigDecimal overallRiskScore,
        RiskTier riskTier,
        String statusMessage,
        RiskDriverBreakdown drivers,
        List<String> recommendations,
        LocalDateTime evaluationWindowStart,
        LocalDateTime evaluationWindowEnd,
        int readingsCount,
        String disclaimer
) {
    public static final String EXPERIMENTAL_DISCLAIMER =
            "Experimental heuristic estimate based on physical cold-chain exposure. " +
            "Not a validated empirical machine learning loss prediction or measured post-harvest loss percentage.";
}
