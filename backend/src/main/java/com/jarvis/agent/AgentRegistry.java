package com.jarvis.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Registry of permanent agents (spec §7.1, full roster) and a factory for
 * temporary agents (spec §7.2). Each agent is data: a system prompt + the set
 * of tools it's allowed to use. Agents whose dedicated tools don't exist yet
 * are given the closest available tools, so the roster is complete and grows as
 * new capabilities/connectors are added.
 */
@Component
public class AgentRegistry {

    private final Map<String, AgentDefinition> bySlug = new LinkedHashMap<>();
    private final AtomicInteger tempCounter = new AtomicInteger();

    public AgentRegistry() {
        for (AgentDefinition a : new AgentDefinitionLoader().load()) {
            bySlug.put(a.slug(), a);
        }
    }

    /**
     * Grant extra tools (e.g. plugin-contributed ones) to the General agent so they're
     * reachable through normal routing. Rebuilds the definition with a deduped tool list.
     */
    public synchronized void grantToolsToGeneral(List<String> toolNames) {
        AgentDefinition g = bySlug.get("general");
        if (g == null || toolNames == null || toolNames.isEmpty()) {
            return;
        }
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(g.toolNames());
        merged.addAll(toolNames);
        bySlug.put("general", new AgentDefinition(g.name(), g.slug(), g.role(),
                g.systemPrompt(), List.copyOf(merged), g.category(), g.keywords(), g.routePriority()));
    }

    /** Remove tools from the General agent (when a plugin is uninstalled). */
    public synchronized void revokeToolsFromGeneral(List<String> toolNames) {
        AgentDefinition g = bySlug.get("general");
        if (g == null || toolNames == null || toolNames.isEmpty()) {
            return;
        }
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(g.toolNames());
        toolNames.forEach(merged::remove);
        bySlug.put("general", new AgentDefinition(g.name(), g.slug(), g.role(),
                g.systemPrompt(), List.copyOf(merged), g.category(), g.keywords(), g.routePriority()));
    }

    public List<AgentDefinition> all() {
        return List.copyOf(bySlug.values());
    }

    public Optional<AgentDefinition> find(String slug) {
        return Optional.ofNullable(bySlug.get(slug));
    }

    public AgentDefinition general() {
        return bySlug.get("general");
    }

    /** Create an ephemeral specialist for a one-off subtask (spec §7.2). */
    public AgentDefinition createTemporary(String role, List<String> toolNames) {
        int n = tempCounter.incrementAndGet();
        return new AgentDefinition("Temporary Agent #" + n, "temp-" + n, role,
                "You are an ephemeral specialist agent. " + role, toolNames, "temporary");
    }
}
