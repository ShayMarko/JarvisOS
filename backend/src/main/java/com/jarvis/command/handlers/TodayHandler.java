package com.jarvis.command.handlers;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.digest.DigestService;

import lombok.RequiredArgsConstructor;

/** {@code /today} — the "Jarvis Today" morning briefing. */
@Component
@RequiredArgsConstructor
public class TodayHandler implements CommandHandler {

    private final DigestService digest;

    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("today", "/today",
                List.of("digest", "morning digest", "what's on today", "daily briefing"),
                "Show today's briefing: calendar, approvals, tasks and recent activity.",
                List.of(), List.of(), true, CommandCategory.MONITORING);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        return CommandResult.ok("today", digest.build(), null);
    }
}
