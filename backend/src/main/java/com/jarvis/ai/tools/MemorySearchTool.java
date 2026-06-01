package com.jarvis.ai.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.memory.Memory;
import com.jarvis.memory.MemoryService;

import lombok.RequiredArgsConstructor;

/** Searches the user's stored memories. */
@Component
@RequiredArgsConstructor
public class MemorySearchTool implements Tool {

    private final MemoryService memory;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("memory_search", "Search the user's stored memories.",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"description\":\"optional\"}}}");
    }

    @Override
    public String execute(String args) {
        List<Memory> hits = memory.list(ToolArgs.str(mapper, args, "query"));
        if (hits.isEmpty()) {
            return "No memories found.";
        }
        return hits.stream().limit(5)
                .map(m -> "- " + m.getTitle() + ": " + m.getContent())
                .reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
