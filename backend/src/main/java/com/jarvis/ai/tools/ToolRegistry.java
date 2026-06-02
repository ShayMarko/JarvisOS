package com.jarvis.ai.tools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;

/**
 * Indexes the available {@link Tool}s by name (the agent runtime resolves tools here).
 * Built-in tools are injected at startup; plugin tools are {@link #register(Tool) added}
 * and {@link #unregister(String) removed} at runtime by the plugin loader, so the map is
 * synchronized for concurrent reads (agent loop) and writes (install/uninstall).
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> byName = Collections.synchronizedMap(new LinkedHashMap<>());

    public ToolRegistry(List<Tool> tools) {
        tools.forEach(t -> byName.put(t.spec().name(), t));
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /** Add a (plugin-contributed) tool at runtime. Returns false if the name is already taken. */
    public boolean register(Tool tool) {
        String name = tool.spec().name();
        synchronized (byName) {
            if (byName.containsKey(name)) {
                return false;
            }
            byName.put(name, tool);
            return true;
        }
    }

    /** Remove a previously-registered tool by name (used when uninstalling a plugin). */
    public void unregister(String name) {
        byName.remove(name);
    }

    public boolean has(String name) {
        return byName.containsKey(name);
    }

    /** The specs for a given set of tool names (an agent's allowed tools). */
    public List<ToolSpec> specsFor(List<String> names) {
        return names.stream().map(byName::get).filter(java.util.Objects::nonNull)
                .map(Tool::spec).toList();
    }

    /** All registered tool specs (for the plugin surface). */
    public List<ToolSpec> all() {
        synchronized (byName) {
            return byName.values().stream().map(Tool::spec).toList();
        }
    }
}
