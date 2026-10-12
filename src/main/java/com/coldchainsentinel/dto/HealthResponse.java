package com.coldchainsentinel.dto;

/**
 * Health check response payload for ColdChain Sentinel.
 */
public record HealthResponse(
        String application,
        String status,
        String message
) {
}
