package com.jarvis.command.handlers;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.web.WebSearchService;

/** {@code /web <query>} — keyless web search (spec §8). */
@Component
@RequiredArgsConstructor
public class WebHandler implements CommandHandler {

    private final WebSearchService web;


    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("web", "/web", List.of("web search", "google"),
                "Search the web (instant answer).", List.of("query"),
                List.of(), true, CommandCategory.SYSTEM);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        String query = context.argLine().trim();
        if (query.isEmpty()) {
            return CommandResult.error("Usage: /web <query>");
        }
        return CommandResult.message("🔎 " + web.search(query));
    }
}
