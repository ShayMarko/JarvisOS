package com.jarvis.command.handlers;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;

/**
 * {@code /stop} — stop the currently running operation (spec §5.2).
 * Placeholder until long-running agents/tasks exist (Phase 6/8) to cancel.
 */
@Component
public class StopHandler implements CommandHandler {

    @Override
    public CommandDefinition definition() {
        return CommandDefinition.simple("stop", "/stop",
                "Stop the current operation.", CommandCategory.SYSTEM);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        return CommandResult.message("Nothing is currently running.");
    }
}
