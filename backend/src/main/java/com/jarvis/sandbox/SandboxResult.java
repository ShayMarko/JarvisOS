package com.jarvis.sandbox;

/** The captured outcome of a sandboxed command run (spec §11.4). */
public record SandboxResult(
        int exitCode,
        String output,
        long durationMs,
        boolean timedOut,
        String workdir
) {}
