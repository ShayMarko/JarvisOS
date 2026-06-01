package com.jarvis.command.handlers;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.explorer.FileNode;
import com.jarvis.explorer.FileSystemService;

/** {@code /searchall <query>} — approved global search across the Jarvis Explorer (spec §5.2). */
@Component
@RequiredArgsConstructor
public class SearchAllHandler implements CommandHandler {

    private final FileSystemService fileSystem;


    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("searchall", "/searchall", List.of("search everywhere"),
                "Search files across the Jarvis Explorer.", List.of("query"),
                List.of("files:read"), true, CommandCategory.FILES);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        String query = context.argLine().trim();
        if (query.isEmpty()) {
            return CommandResult.error("Usage: /searchall <query>");
        }
        List<FileNode> matches = fileSystem.search(query, "");
        return CommandResult.ok("files", "Search results for \"" + query + "\" (" + matches.size() + ")",
                Map.of("path", "", "entries", matches));
    }
}
