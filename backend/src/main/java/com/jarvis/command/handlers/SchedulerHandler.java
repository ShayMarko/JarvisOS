package com.jarvis.command.handlers;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.workflow.TriggerType;
import com.jarvis.workflow.WorkflowService;

/** {@code /scheduler} — shows scheduled (cron) workflows (spec §5.2, §12). */
@Component
@RequiredArgsConstructor
public class SchedulerHandler implements CommandHandler {

    private final WorkflowService workflows;


    @Override
    public CommandDefinition definition() {
        return CommandDefinition.simple("scheduler", "/scheduler",
                "Show scheduled (cron) workflows.", CommandCategory.WORKFLOWS);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        var scheduled = workflows.list().stream()
                .filter(w -> w.triggerType() == TriggerType.SCHEDULE)
                .toList();
        return CommandResult.ok("workflows", "Scheduler", java.util.Map.of("workflows", scheduled, "runs", java.util.List.of()));
    }
}
