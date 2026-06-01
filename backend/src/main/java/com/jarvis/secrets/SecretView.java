package com.jarvis.secrets;

import java.time.Instant;
import java.util.List;

/**
 * The safe, maskable projection of a {@link Secret} for the API/UI — never
 * contains the plaintext value.
 */
public record SecretView(
        String id,
        String name,
        String connector,
        List<String> scopes,
        String masked,
        Instant createdAt,
        Instant lastAccessedAt
) {}
