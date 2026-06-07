package com.jarvis.agent;

import java.util.List;

/**
 * A unit of expertise (spec §7) — not a separate process, but a prompt + a set of allowed tools + context.
 * Permanent agents are registered at startup from markdown files; temporary agents can be created per task.
 *
 * <p>{@code keywords} + {@code routePriority} make an agent fully self-routing: the AgentSelector builds its
 * ordered keyword table from these (lower priority = checked first), so adding/retuning an agent is a
 * single-file edit — no separate Java rule.
 */
public record AgentDefinition(
        String name,
        String slug,
        String role,
        String systemPrompt,
        List<String> toolNames,
        String category,
        List<String> keywords,
        int routePriority
) {

    /** Default priority for agents that declare no routing keywords (they never win a keyword match anyway). */
    public static final int DEFAULT_PRIORITY = 1000;

    /** Convenience for agents/tests/temp agents that carry no routing keywords. */
    public AgentDefinition(String name, String slug, String role, String systemPrompt,
                           List<String> toolNames, String category) {
        this(name, slug, role, systemPrompt, toolNames, category, List.of(), DEFAULT_PRIORITY);
    }
}
