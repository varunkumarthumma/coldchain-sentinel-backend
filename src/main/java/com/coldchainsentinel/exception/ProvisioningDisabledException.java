package com.coldchainsentinel.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when device token provisioning is attempted in an environment where open provisioning
 * is disabled without administrative authorization.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ProvisioningDisabledException extends RuntimeException {

    public ProvisioningDisabledException(String message) {
        super(message);
    }
}
