package com.coldchainsentinel.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when sensor device authentication fails (missing, invalid, or mismatched token).
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedDeviceException extends RuntimeException {

    public UnauthorizedDeviceException(String message) {
        super(message);
    }
}
