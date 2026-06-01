package com.jarvis.explorer;

import java.time.Instant;

/**
 * A single entry in the Jarvis Explorer. {@code path} is relative to the
 * Jarvis Explorer root so the client can navigate without ever seeing or
 * sending absolute system paths.
 */
public record FileNode(
        String name,
        String path,
        boolean directory,
        long size,
        Instant modified
) {}
