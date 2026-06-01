package com.jarvis.command.handlers;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandRegistry;
import com.jarvis.command.CommandResult;

/** {@code /help} — lists the available commands (spec §5.2). */
@Component
@RequiredArgsConstructor
public class HelpHandler implements CommandHandler {

    // @Lazy breaks the cycle: registry depends on all handlers, this handler reads the registry.
    @Lazy
    private final CommandRegistry registry;

    @Override
    public CommandDefinition definition() {
        return CommandDefinition.simple("help", "/help",
                "Show the available commands.", CommandCategory.SYSTEM);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        var commands = registry.definitions().stream()
                .filter(CommandDefinition::visibleInHelp)
                .toList();
        return CommandResult.ok("help", "Available commands", commands);
    }
}
