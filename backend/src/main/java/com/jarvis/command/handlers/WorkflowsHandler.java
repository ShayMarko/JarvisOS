package com.jarvis.command.handlers;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.workflow.WorkflowService;

/** {@code /workflows} — manage workflows and view recent runs (spec §5.2, §12). */
@Component
public class WorkflowsHandler implements CommandHandler {

    private final WorkflowService workflows;

    public WorkflowsHandler(WorkflowService workflows) {
        this.workflows = workflows;
    }

    @Override
    public CommandDefinition definition() {
        return CommandDefinition.simple("workflows", "/workflows",
                "Manage workflows and automations.", CommandCategory.WORKFLOWS);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workflows", workflows.list());
        data.put("runs", workflows.recentRuns(20));
        return CommandResult.ok("workflows", "Workflows", data);
    }
}
