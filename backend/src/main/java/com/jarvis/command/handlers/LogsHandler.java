package com.jarvis.command.handlers;

import org.springframework.stereotype.Component;

import com.jarvis.audit.AuditService;
import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;

import java.util.List;

/** {@code /logs [limit]} — shows recent audit-log entries (spec §5.2). */
@Component
public class LogsHandler implements CommandHandler {

    private final AuditService audit;

    public LogsHandler(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("logs", "/logs", List.of("show logs", "audit log"),
                "Show recent audit logs.", List.of("limit"),
                List.of(), true, CommandCategory.MONITORING);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        int limit = 50;
        if (!context.args().isEmpty()) {
            try {
                limit = Math.max(1, Math.min(500, Integer.parseInt(context.args().get(0))));
            } catch (NumberFormatException ignored) {
                // fall back to the default limit
            }
        }
        return CommandResult.ok("logs", "Recent audit logs", audit.recent(limit));
    }
}
