package com.jarvis.command;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Indexes every {@link CommandHandler} bean by its slash token. Acts as the
 * "Command Registry" of the spec — the single source of truth for which
 * commands exist and what metadata /help shows.
 */
@Component
public class CommandRegistry {

    /** Friendly aliases → canonical slash (familiar names from older Jarvis). */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("/files", "/jfiles"),
            Map.entry("/explorer", "/jfiles"),
            Map.entry("/file-explorer", "/jfiles"),
            Map.entry("/config", "/settings"),
            Map.entry("/runs", "/tasks"),
            Map.entry("/history", "/tasks"),
            Map.entry("/integrations", "/connectors"),
            Map.entry("/automations", "/workflows"),
            Map.entry("/search", "/searchall"));

    private final Map<String, CommandHandler> bySlash = new LinkedHashMap<>();

    public CommandRegistry(List<CommandHandler> handlers) {
        handlers.stream()
                .sorted(Comparator.comparing(h -> h.definition().slash()))
                .forEach(h -> bySlash.put(h.definition().slash().toLowerCase(), h));
    }

    private String canonical(String slash) {
        String s = slash.toLowerCase();
        return ALIASES.getOrDefault(s, s);
    }

    public Optional<CommandHandler> find(String slash) {
        if (slash == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(bySlash.get(canonical(slash)));
    }

    public boolean isCommand(String slash) {
        return slash != null && bySlash.containsKey(canonical(slash));
    }

    public List<CommandDefinition> definitions() {
        return bySlash.values().stream().map(CommandHandler::definition).toList();
    }
}
