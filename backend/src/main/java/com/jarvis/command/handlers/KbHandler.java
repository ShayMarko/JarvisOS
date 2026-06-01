package com.jarvis.command.handlers;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.kb.KnowledgeBaseService;

import java.util.List;

/** {@code /kb [query]} — open the Knowledge Base, or run a semantic search (spec §5.2, §10.2). */
@Component
public class KbHandler implements CommandHandler {

    private final KnowledgeBaseService kb;

    public KbHandler(KnowledgeBaseService kb) {
        this.kb = kb;
    }

    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("kb", "/kb", List.of("knowledge base", "open kb"),
                "Open the Knowledge Base (or /kb <query> to search).", List.of("query"),
                List.of(), true, CommandCategory.MEMORY);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        String query = context.argLine().trim();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("documents", kb.documents());
        data.put("query", query);
        data.put("results", query.isEmpty() ? List.of() : kb.search(query, 8));
        return CommandResult.ok("kb", query.isEmpty() ? "Knowledge Base" : "KB search: " + query, data);
    }
}
