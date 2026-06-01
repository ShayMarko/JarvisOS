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
    public String execute(String args) {
        try {
            String path = ToolArgs.str(mapper, args, "path");
            fs.writeText(path, ToolArgs.str(mapper, args, "content"));
            return "Wrote " + path;
        } catch (Exception e) {
            return "Error writing file: " + e.getMessage();
        }
    }
}
