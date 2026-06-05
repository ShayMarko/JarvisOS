package com.jarvis.common;

import java.util.UUID;

/**
 * Generates short, prefixed, collision-resistant ids — the {@code "<prefix>_<hex>"} convention used for
 * every entity in Jarvis (mem_…, ntf_…, ap_…, run_…). Centralised so the format stays consistent.
 */
public final class Ids {

    private Ids() {
    }

    /** A new id like {@code "mem_3f9a1c2b"} (8 hex chars). */
    public static String generate(String prefix) {
        return generate(prefix, 8);
    }

    /** A new id with a custom hex length. */
    public static String generate(String prefix, int hexLength) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, hexLength);
    }
}
