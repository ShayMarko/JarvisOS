package com.jarvis.command.handlers;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.job.Job;
import com.jarvis.job.JobService;

/** {@code /jobs} — list background jobs, or {@code /jobs <task>} to run a long task detached. */
@Component
@RequiredArgsConstructor
public class JobsHandler implements CommandHandler {

    private final JobService jobs;

    @Override
    public CommandDefinition definition() {
        return CommandDefinition.simple("jobs", "/jobs",
                "List background jobs, or '/jobs <task>' to run a long task in the background.",
                CommandCategory.AGENTS);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        String arg = context.argLine().trim();
        if (!arg.isBlank()) {
            Job j = jobs.submit(arg, "command", "command");
            return CommandResult.ok("job", "Started background job " + j.getId()
                    + " — I'll notify you when it's done.", j);
        }
        return CommandResult.ok("jobs", "Background jobs", jobs.recent(30));
    }
}
