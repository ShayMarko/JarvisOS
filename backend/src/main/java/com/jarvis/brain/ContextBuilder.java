package com.jarvis.brain;

import org.springframework.stereotype.Component;

import com.jarvis.memory.Memory;
import com.jarvis.memory.MemoryService;

/**
 * Assembles context for the Brain (spec §6 "Context Builder"). Phase 6 pulls in
 * the user's active memories; later phases add KB/RAG, conversation and files.
 */
@Component
public class ContextBuilder {

    private static final int MAX_MEMORIES = 5;

    private final MemoryService memory;

    public ContextBuilder(MemoryService memory) {
        this.memory = memory;
    }

    public String build() {
        StringBuilder sb = new StringBuilder();
        memory.list("").stream()
                .filter(Memory::isActive)
                .limit(MAX_MEMORIES)
                .forEach(m -> sb.append("- ").append(m.getTitle()).append(": ").append(m.getContent()).append("\n"));
        if (sb.isEmpty()) {
            return "";
        }
        return "What you know about the user:\n" + sb;
    }
}
