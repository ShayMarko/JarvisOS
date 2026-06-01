package com.jarvis.command.handlers;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.system.SystemMonitorService;

/** {@code /status} — overall system status (spec §5.2). */
@Component
@RequiredArgsConstructor
public class StatusHandler implements CommandHandler {

    private final SystemMonitorService monitor;


    @Override
    public CommandDefinition definition() {
        return CommandDefinition.simple("status", "/status",
                "Show overall Jarvis and system status.", CommandCategory.MONITORING);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        return CommandResult.ok("status", "System status", monitor.snapshot());
    }
}
