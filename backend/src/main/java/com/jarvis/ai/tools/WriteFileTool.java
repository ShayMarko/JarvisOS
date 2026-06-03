package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.explorer.FileSystemService;

import lombok.RequiredArgsConstructor;

/** Creates or overwrites a text file in the Jarvis Explorer. */
@Component
@RequiredArgsConstructor
public class WriteFileTool implements Tool {

    private final FileSystemService fs;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("write_file", "Create or overwrite a text file in the Jarvis Explorer.",
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},"
                + "\"content\":{\"type\":\"string\"}},\"required\":[\"path\",\"content\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String args) {
        try {
            // Accept the common argument-name variants weaker models emit (e.g. "filename" for path,
            // "code"/"text" for content) so a build doesn't silently fail with an empty path.
            String path = ToolArgs.firstStr(mapper, args, "path", "file_path", "filepath", "file", "filename", "name");
            String content = ToolArgs.firstStr(mapper, args, "content", "text", "body", "code", "data", "file_content");
            if (path == null || path.isBlank()) {
                return "Error: no file path provided. Call write_file with a \"path\" (e.g. "
                        + "Projects/<app>/backend/src/Main.java) and \"content\".";
            }
            fs.writeText(path, content);
            return "Wrote " + path;
        } catch (Exception e) {
            return "Error writing file: " + e.getMessage();
        }
    }
}
