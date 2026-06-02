package com.jarvis.error;

/** Concrete domain exceptions, grouped for convenience. */
public final class Exceptions {

    private Exceptions() {}

    /** A path is on the always-blocked list, or escapes its allowed root. */
    public static class PathBlockedException extends JarvisException {
        public PathBlockedException(String message) {
            super(ErrorCode.PATH_BLOCKED, message);
        }
    }

    /** The current permission mode / folder permission forbids the operation. */
    public static class PermissionDeniedException extends JarvisException {
        public PermissionDeniedException(String message) {
            super(ErrorCode.PERMISSION_DENIED, message);
        }
    }

    /** The action is allowed but needs explicit human approval first (spec §11.2). */
    public static class ApprovalRequiredException extends JarvisException {
        public ApprovalRequiredException(String message) {
            super(ErrorCode.APPROVAL_REQUIRED, message);
        }
    }

    /** The action is forbidden outright by the declarative restrictions policy (never runs). */
    public static class PolicyDeniedException extends JarvisException {
        public PolicyDeniedException(String message) {
            super(ErrorCode.PERMISSION_DENIED, message);
        }
    }

    /** The request is malformed or fails a precondition (HTTP 400). */
    public static class ValidationException extends JarvisException {
        public ValidationException(String message) {
            super(ErrorCode.VALIDATION_FAILED, message);
        }
    }

    /** A requested file/resource does not exist. */
    public static class NotFoundException extends JarvisException {
        public NotFoundException(String message) {
            super(ErrorCode.NOT_FOUND, message);
        }
    }

    /** A conflicting state (e.g. file already exists). */
    public static class ConflictException extends JarvisException {
        public ConflictException(String message) {
            super(ErrorCode.CONFLICT, message);
        }
    }

    /** An external connector call failed (spec §9). */
    public static class ConnectorException extends JarvisException {
        public ConnectorException(String message) {
            super(ErrorCode.CONNECTOR_ERROR, message);
        }
    }
}
