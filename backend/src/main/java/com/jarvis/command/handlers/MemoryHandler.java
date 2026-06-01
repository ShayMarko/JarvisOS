package com.jarvis.command.handlers;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.memory.MemoryService;

import java.util.List;

/** {@code /memory [query]} — opens the Memory Manager (spec §5.2). */
@Component
@RequiredArgsConstructor
public class MemoryHandler implements CommandHandler {

    private final MemoryService memory;


    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("memory", "/memory", List.of("open memory", "show memory"),
                "Open the Memory Manager.", List.of("query"),
                List.of(), true, CommandCategory.MEMORY);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        String query = context.argLine().trim();
        return CommandResult.ok("memory",
                query.isEmpty() ? "Memory Manager" : "Memory matching \"" + query + "\"",
                memory.list(query));
    }
}
