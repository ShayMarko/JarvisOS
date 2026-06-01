package com.jarvis.command;

/**
 * Implemented by every slash command. Each handler is a Spring bean; the
 * {@link CommandRegistry} discovers them all at startup and indexes them by
 * their slash token. "Slash command always wins" — a recognised command never
 * falls through to free AI interpretation (spec §5).
 */
public interface CommandHandler {

    CommandDefinition definition();

    CommandResult handle(CommandContext context);
}
