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

/** {@code /jfiles [path]} — lists files in the Jarvis Explorer (spec §5.2, §14.1). */
@Component
@RequiredArgsConstructor
public class JFilesHandler implements CommandHandler {

    private final FileSystemService fileSystem;


    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("jfiles", "/jfiles", List.of("show my files", "open files"),
                "List files in the Jarvis Explorer.", List.of("path"),
                List.of("files:read"), true, CommandCategory.FILES);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        String path = context.argLine();
        List<FileNode> nodes = fileSystem.list(path);
        return CommandResult.ok("files", "Jarvis Explorer: /" + path,
                Map.of("path", path, "entries", nodes));
    }
}
