package com.jarvis.ai.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;
import com.jarvis.project.ProjectInfo;
import com.jarvis.project.ProjectScanner;

import lombok.RequiredArgsConstructor;

/** Lists the developer projects Jarvis can find on the machine. */
@Component
@RequiredArgsConstructor
public class ListProjectsTool implements Tool {

    private final ProjectScanner scanner;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("list_projects",
                "List developer projects detected under the configured project folders, with their type and IDE.",
                "{\"type\":\"object\",\"properties\":{}}");
    }

    @Override
    public String execute(String args) {
        try {
            List<ProjectInfo> projects = scanner.scan();
            if (projects.isEmpty()) {
                return "No projects found under the configured scan folders.";
            }
            StringBuilder sb = new StringBuilder();
            for (ProjectInfo p : projects) {
                sb.append("• ").append(p.name())
                        .append(" (").append(p.type()).append(", opens in ").append(p.ide()).append(")\n")
                        .append("    ").append(p.path()).append('\n');
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Error listing projects: " + e.getMessage();
        }
    }
}
