package com.jarvis.command.handlers;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.connectors.ConnectorRegistry;

/** {@code /connectors} — shows connectors / MCPs and their health (spec §9). */
@Component
public class ConnectorsHandler implements CommandHandler {

    private final ConnectorRegistry registry;

    public ConnectorsHandler(ConnectorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("connectors", "/connectors", List.of("show connectors", "integrations"),
                "Show connectors / MCPs and their health.", List.of(), List.of(), true, CommandCategory.SYSTEM);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        return CommandResult.ok("connectors", "Connectors / MCPs", registry.list());
    }
}
