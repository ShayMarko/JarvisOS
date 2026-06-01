package com.jarvis.input;

import java.util.List;

import com.jarvis.command.CommandContext;

/**
 * The Input Router's decision about a piece of input (spec §4). Phase 1
 * distinguishes deterministic slash commands from everything else (which would
 * go to the Jarvis Brain once that exists).
 */
public record RoutedInput(Type type, CommandContext command, String text) {

    public enum Type {
        /** Recognised "/..." command — goes to the Command Engine. */
        SLASH_COMMAND,
        /** An unrecognised "/..." token. */
        UNKNOWN_COMMAND,
        /** Free text / AI request — destined for the Brain (not yet built). */
        AI_REQUEST,
        /** Empty input. */
        EMPTY
    }

    static RoutedInput slashCommand(String raw, String slash, List<String> args) {
        return new RoutedInput(Type.SLASH_COMMAND, new CommandContext(raw, slash, args), raw);
    }

    static RoutedInput unknownCommand(String raw, String slash, List<String> args) {
        return new RoutedInput(Type.UNKNOWN_COMMAND, new CommandContext(raw, slash, args), raw);
    }

    static RoutedInput aiRequest(String raw) {
        return new RoutedInput(Type.AI_REQUEST, null, raw);
    }

    static RoutedInput empty() {
        return new RoutedInput(Type.EMPTY, null, "");
    }
}
