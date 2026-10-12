package com.coldchainsentinel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Security configuration controlling device token enforcement and provisioning policies.
 *
 * In production environments, unprovisioned sensor ingestion and open token provisioning
 * must remain strictly disabled (false).
 * In local development or demonstration environments, these can be explicitly enabled
 * via the 'dev' Spring profile or configuration properties.
 */
@Component
@ConfigurationProperties(prefix = "coldchain.security")
public class DeviceSecurityProperties {

    /**
     * Whether unprovisioned sensors (sensors without a provisioned token hash)
     * are permitted to ingest readings without an authentication token.
     * Default is false (strictly enforced in production).
     */
    private boolean allowUnprovisionedSensors = false;

    /**
     * Whether open token provisioning and rotation via POST /api/sensors/{id}/token
     * is permitted without administrative authentication.
     * Default is false (strictly disabled in production).
     */
    private boolean allowOpenProvisioning = false;

    public DeviceSecurityProperties() {
    }

    public DeviceSecurityProperties(boolean allowUnprovisionedSensors, boolean allowOpenProvisioning) {
        this.allowUnprovisionedSensors = allowUnprovisionedSensors;
        this.allowOpenProvisioning = allowOpenProvisioning;
    }

    public boolean isAllowUnprovisionedSensors() {
        return allowUnprovisionedSensors;
    }

    public void setAllowUnprovisionedSensors(boolean allowUnprovisionedSensors) {
        this.allowUnprovisionedSensors = allowUnprovisionedSensors;
    }

    public boolean isAllowOpenProvisioning() {
        return allowOpenProvisioning;
    }

    public void setAllowOpenProvisioning(boolean allowOpenProvisioning) {
        this.allowOpenProvisioning = allowOpenProvisioning;
    }
}
