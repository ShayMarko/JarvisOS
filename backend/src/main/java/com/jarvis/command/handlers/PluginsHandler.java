package com.jarvis.command.handlers;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.plugin.PluginRegistry;

/** {@code /plugins} — show the extension surface (spec §5.2, §8). */
@Component
@RequiredArgsConstructor
public class PluginsHandler implements CommandHandler {

    private final PluginRegistry plugins;


    @Override
    public CommandDefinition definition() {
        return CommandDefinition.simple("plugins", "/plugins",
                "Show installed commands, tools, connectors and agents.", CommandCategory.SYSTEM);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        return CommandResult.ok("plugins", "Plugins & Extensions", plugins.surface());
    }
}
