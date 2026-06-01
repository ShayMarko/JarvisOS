package com.jarvis.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.jarvis.common.logging.TraceIdFilter;

/**
 * Translates exceptions into the single {@link ApiError} envelope. Domain
 * ({@link JarvisException}) errors are expected and logged at WARN; anything
 * else is unexpected, logged at ERROR with the stack trace, and reported to
 * the client as a generic 500 (never leaking internals).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(JarvisException.class)
    public ResponseEntity<ApiError> handleJarvis(JarvisException ex) {
        String traceId = TraceIdFilter.currentTraceId();
        log.warn("[{}] {} - {}", ex.code(), ex.getClass().getSimpleName(), ex.getMessage());
        ApiError body = ApiError.of(ex.code(), ex.getMessage(), List.of(), traceId);
        return ResponseEntity.status(ex.code().status()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        ApiError body = ApiError.of(ErrorCode.VALIDATION_FAILED, "Validation failed",
                details, TraceIdFilter.currentTraceId());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        ApiError body = ApiError.of(ErrorCode.VALIDATION_FAILED, ex.getMessage(),
                List.of(), TraceIdFilter.currentTraceId());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        String traceId = TraceIdFilter.currentTraceId();
        log.error("[{}] Unexpected error", traceId, ex);
        ApiError body = ApiError.of(ErrorCode.INTERNAL,
                "An unexpected error occurred. Reference: " + traceId, List.of(), traceId);
        return ResponseEntity.status(ErrorCode.INTERNAL.status()).body(body);
    }
}
