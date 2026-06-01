package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.memory.MemoryDraft;
import com.jarvis.memory.MemoryService;

import lombok.RequiredArgsConstructor;

/** Saves a durable fact about the user for future conversations. */
@Component
@RequiredArgsConstructor
public class MemoryWriteTool implements Tool {

    private final MemoryService memory;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("memory_write",
                "Save a durable fact about the user so you remember it in future conversations.",
                "{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"},"
                + "\"content\":{\"type\":\"string\"},\"category\":{\"type\":\"string\",\"description\":\"e.g. preference, fact, project\"}},"
                + "\"required\":[\"title\",\"content\"]}");
    }

    @Override
    public String execute(String args) {
        try {
            String title = ToolArgs.str(mapper, args, "title");
            String content = ToolArgs.str(mapper, args, "content");
            String category = ToolArgs.str(mapper, args, "category");
            if (content.isBlank()) {
                return "Nothing to remember (empty content).";
            }
            memory.create(new MemoryDraft(category.isBlank() ? "fact" : category,
                    title.isBlank() ? "Note" : title, content, "chat", null, null, null, null, null));
            return "Remembered: " + (title.isBlank() ? content : title);
        } catch (Exception e) {
            return "Error saving memory: " + e.getMessage();
        }
    }
}
