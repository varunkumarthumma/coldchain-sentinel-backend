package com.coldchainsentinel.dto;

import java.time.LocalDateTime;

/**
 * Response payload delivered once upon provisioning or rotating a sensor's device token.
 * The raw device token is never stored in plaintext and cannot be retrieved again.
 */
public record ProvisionDeviceTokenResponse(
        Long sensorId,
        String sensorCode,
        String deviceToken,
        LocalDateTime generatedAt,
        String instructions
) {
    public static ProvisionDeviceTokenResponse of(Long sensorId, String sensorCode, String rawToken) {
        return new ProvisionDeviceTokenResponse(
                sensorId,
                sensorCode,
                rawToken,
                LocalDateTime.now(),
                "Store this token securely on the IoT device. Include it in the 'X-Sensor-Token' header for telemetry ingestion."
        );
    }
}
