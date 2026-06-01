package com.jarvis.error;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape returned by the API. {@code traceId} lets a user
 * report a failure that can be correlated with server logs.
 */
public record ApiError(
        String code,
        String message,
        List<String> detail,
        String traceId,
        Instant timestamp
) {
    public static ApiError of(ErrorCode code, String message, List<String> detail, String traceId) {
        return new ApiError(code.name(), message, detail, traceId, Instant.now());
    }
}
