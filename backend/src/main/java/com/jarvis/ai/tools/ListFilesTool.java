package com.jarvis.ai.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.explorer.FileNode;
import com.jarvis.explorer.FileSystemService;

import lombok.RequiredArgsConstructor;

/** Lists files/folders in the Jarvis Explorer. */
@Component
@RequiredArgsConstructor
public class ListFilesTool implements Tool {

    private final FileSystemService fs;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("list_files", "List files/folders in the Jarvis Explorer.",
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"relative path, optional\"}}}");
    }

    @Override
    public String execute(String args) {
        try {
            String path = ToolArgs.str(mapper, args, "path");
            List<FileNode> nodes = fs.list(path);
            if (nodes.isEmpty()) {
                return "(/" + path + " is empty)";
            }
            return "/" + path + ":\n" + nodes.stream()
                    .map(n -> (n.directory() ? "📁 " : "📄 ") + n.name())
                    .reduce((a, b) -> a + "\n" + b).orElse("");
        } catch (Exception e) {
            return "Error listing files: " + e.getMessage();
        }
    }
}
