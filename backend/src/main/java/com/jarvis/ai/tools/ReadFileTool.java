package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.explorer.FileSystemService;

import lombok.RequiredArgsConstructor;

/** Reads the text contents of a file. */
@Component
@RequiredArgsConstructor
public class ReadFileTool implements Tool {

    private final FileSystemService fs;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("read_file", "Read the text contents of a file.",
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}");
    }

    @Override
    public String execute(String args) {
        try {
            String content = fs.readText(ToolArgs.str(mapper, args, "path")).content();
            return content.length() > 1500 ? content.substring(0, 1500) + "\n…(truncated)" : content;
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}
