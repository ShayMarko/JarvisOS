package com.jarvis.command.handlers;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.system.SystemMonitorService;

import java.util.List;

/** {@code /resources} — opens the live System Resource Dashboard (spec §5.2, §13). */
@Component
public class ResourcesHandler implements CommandHandler {

    private final SystemMonitorService monitor;

    public ResourcesHandler(SystemMonitorService monitor) {
        this.monitor = monitor;
    }

    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("resources", "/resources", List.of("show resources", "system monitor"),
                "Open the live System Resource Dashboard.", List.of(),
                List.of(), true, CommandCategory.MONITORING);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        // Initial snapshot; the client then subscribes to /api/monitor/stream for live updates.
        return CommandResult.ok("resources", "System Resource Dashboard", monitor.snapshot());
    }
}
