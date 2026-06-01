package com.jarvis.ai.tools;

import java.nio.file.Path;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.local.MacActions;
import com.jarvis.project.ProjectInfo;
import com.jarvis.project.ProjectScanner;

import lombok.RequiredArgsConstructor;

/** Opens a named developer project in the IDE appropriate to its type. */
@Component
@RequiredArgsConstructor
public class OpenProjectTool implements Tool {

    private final ProjectScanner scanner;
    private final MacActions mac;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("open_project",
                "Open a developer project by name in the right IDE (detected from its project type).",
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\"]}");
    }

    @Override
    public String execute(String args) {
        try {
            String name = ToolArgs.str(mapper, args, "name");
            Optional<ProjectInfo> match = scanner.findByName(name);
            if (match.isEmpty()) {
                return "No project matching \"" + name + "\". Use list_projects to see what's available.";
            }
            ProjectInfo p = match.get();
            return mac.openPathWith(p.ide(), Path.of(p.path()));
        } catch (Exception e) {
            return "Error opening project: " + e.getMessage();
        }
    }
}
