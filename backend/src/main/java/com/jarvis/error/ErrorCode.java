package com.jarvis.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned to the client alongside a
 * human-readable message. Each maps to an HTTP status.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    PERMISSION_DENIED(HttpStatus.FORBIDDEN),
    PATH_BLOCKED(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    APPROVAL_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY),
    CONNECTOR_ERROR(HttpStatus.BAD_GATEWAY),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
