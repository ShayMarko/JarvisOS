package com.jarvis.command.handlers;

import org.springframework.stereotype.Component;

import com.jarvis.agent.AgentRegistry;
import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;

/** {@code /agents} — lists active/permanent agents (spec §5.2, §7). */
@Component
public class AgentsHandler implements CommandHandler {

    private final AgentRegistry registry;

    public AgentsHandler(AgentRegistry registry) {
        this.registry = registry;
    }

    @Override
    public CommandDefinition definition() {
        return CommandDefinition.simple("agents", "/agents",
                "Show available agents.", CommandCategory.AGENTS);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        return CommandResult.ok("agents", "Agents", registry.all());
    }
}
