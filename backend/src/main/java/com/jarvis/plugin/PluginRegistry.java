package com.jarvis.plugin;

import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.jarvis.agent.AgentRegistry;
import com.jarvis.ai.tools.ToolRegistry;
import com.jarvis.command.CommandRegistry;
import com.jarvis.connectors.ConnectorRegistry;

/**
 * The Plugin / Extension surface (spec §8 Plugin/Extension SDK). Jarvis is
 * extended by adding beans at four extension points — commands, tools,
 * connectors, agents — which are auto-discovered at startup. This registry
 * introspects those live extension points so {@code /plugins} shows exactly
 * what's installed. (A dynamic external-jar loader + manifest install flow is a
 * later step; the extension contracts already exist.)
 */
@Component
@RequiredArgsConstructor
public class PluginRegistry {

    // @Lazy breaks the cycle: CommandRegistry depends on handlers that depend on this registry.
    @Lazy
    private final CommandRegistry commands;
    private final ToolRegistry tools;
    private final ConnectorRegistry connectors;
    private final AgentRegistry agents;


    public Map<String, Object> surface() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("commands", commands.definitions().stream().map(d -> Map.of(
                "slash", d.slash(), "description", d.description(), "category", d.category().name())).toList());
        out.put("tools", tools.all().stream().map(t -> Map.of(
                "name", t.name(), "description", t.description())).toList());
        out.put("connectors", connectors.list().stream().map(c -> Map.of(
                "id", c.id(), "name", c.name(), "status", c.status().name())).toList());
        out.put("agents", agents.all().stream().map(a -> Map.of(
                "slug", a.slug(), "name", a.name(), "role", a.role())).toList());
        out.put("counts", Map.of(
                "commands", commands.definitions().size(),
                "tools", tools.all().size(),
                "connectors", connectors.list().size(),
                "agents", agents.all().size()));
        return out;
    }
}
