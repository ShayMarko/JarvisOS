package com.jarvis.ai.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;

/** Indexes the available {@link Tool}s by name (the agent runtime resolves tools here). */
@Component
public class ToolRegistry {

    private final Map<String, Tool> byName = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> tools) {
        tools.forEach(t -> byName.put(t.spec().name(), t));
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /** The specs for a given set of tool names (an agent's allowed tools). */
    public List<ToolSpec> specsFor(List<String> names) {
        return names.stream().map(byName::get).filter(java.util.Objects::nonNull)
                .map(Tool::spec).toList();
    }

    /** All registered tool specs (for the plugin surface). */
    public List<ToolSpec> all() {
        return byName.values().stream().map(Tool::spec).toList();
    }
}

