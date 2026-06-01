package com.jarvis.command.handlers;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.task.TaskService;

/** {@code /tasks} — shows recent tasks the Brain handled (spec §5.2, §6). */
@Component
@RequiredArgsConstructor
public class TasksHandler implements CommandHandler {

    private final TaskService tasks;


    @Override
    public CommandDefinition definition() {
        return CommandDefinition.simple("tasks", "/tasks",
                "Show open and recent tasks.", CommandCategory.AGENTS);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        return CommandResult.ok("tasks", "Tasks", tasks.recent(50));
    }
}
