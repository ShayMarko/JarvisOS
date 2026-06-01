package com.jarvis.model;

/**
 * Metadata about a model the Model Router can choose (spec §6 Model Router,
 * §13 Cost Monitor). Costs are USD per 1k tokens.
 */
public record ModelDescriptor(
        String id,
        String provider,
        boolean local,
        double costInputPer1k,
        double costOutputPer1k,
        int quality,       // 1–5
        int latencyMs,     // rough estimate
        boolean available  // is this model actually wired up right now?
) {}
