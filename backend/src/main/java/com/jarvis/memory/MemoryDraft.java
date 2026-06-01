package com.jarvis.memory;

import java.time.Instant;

/**
 * Writable fields of a memory. Used for both create and update; on update,
 * {@code null} fields are left unchanged (the required fields are enforced at
 * the API layer for create).
 */
public record MemoryDraft(
        String category,
        String title,
        String content,
        String source,
        Double confidence,
        Visibility visibility,
        Sensitivity sensitivity,
        Instant expiresAt,
        Boolean enabled
) {}
