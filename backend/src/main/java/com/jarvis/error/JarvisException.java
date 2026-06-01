package com.jarvis.error;

/**
 * Base for all expected, domain-level Jarvis errors. Carries an
 * {@link ErrorCode} so the API layer can translate it into a consistent
 * response without leaking internals.
 */
public class JarvisException extends RuntimeException {

    private final ErrorCode code;

    public JarvisException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public JarvisException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
