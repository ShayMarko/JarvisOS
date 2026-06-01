package com.jarvis.command;

import java.util.List;

/**
 * Metadata describing a slash command (spec §5.1). Returned to the client so
 * the /help window and command palette can render the available commands.
 */
public record CommandDefinition(
        String name,
        String slash,
        List<String> voiceAliases,
        String description,
        List<String> parameters,
        List<String> requiredPermissions,
        boolean visibleInHelp,
        CommandCategory category
) {
    /** Convenience builder for a simple, no-parameter, no-permission command. */
    public static CommandDefinition simple(String name, String slash, String description,
                                           CommandCategory category) {
        return new CommandDefinition(name, slash, List.of(), description,
                List.of(), List.of(), true, category);
    }
}
