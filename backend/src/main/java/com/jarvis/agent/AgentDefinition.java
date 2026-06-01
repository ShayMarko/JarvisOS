package com.jarvis.agent;

import java.util.List;

/**
 * A unit of expertise (spec §7) — not a separate process, but a prompt + a set
 * of allowed tools + context. Permanent agents are registered at startup;
 * temporary agents can be created per task.
 */
public record AgentDefinition(
        String name,
        String slug,
        String role,
        String systemPrompt,
        List<String> toolNames,
        String category
) {}
