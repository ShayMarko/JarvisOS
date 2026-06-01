package com.jarvis.security;

/** A file-system operation, used by the {@link PermissionGuard}. */
public enum Operation {
    READ(false),
    WRITE(true),
    CREATE(true),
    DELETE(true);

    private final boolean mutating;

    Operation(boolean mutating) {
        this.mutating = mutating;
    }

    public boolean isMutating() {
        return mutating;
    }
}
