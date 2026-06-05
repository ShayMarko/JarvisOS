package com.jarvis.ai.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.observability.AgentRunRecord;
import com.jarvis.observability.ObservabilityService;

import lombok.RequiredArgsConstructor;

/** Reads recent agent runs (prompt → agent → status) — so "what did I run / recent tasks" works anywhere. */
@Component
@RequiredArgsConstructor
public class RecentRunsTool implements Tool {

    private final ObservabilityService observability;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("recent_runs",
                "List recent agent runs / task history: the prompt, which agent handled it, and its status. "
                + "Use when asked 'what did I run', 'recent tasks', 'my history', 'what have I asked'. "
                + "Optional 'limit' (default 10).",
                "{\"type\":\"object\",\"properties\":{\"limit\":{\"type\":\"integer\"}}}");
    }

    @Override
    public String execute(String argumentsJson) {
        int limit = Math.max(1, Math.min(50, ToolArgs.intVal(mapper, argumentsJson, "limit", 10)));
        List<AgentRunRecord> runs = observability.recent(limit);
        if (runs.isEmpty()) {
            return "No runs recorded yet.";
        }
        StringBuilder sb = new StringBuilder("🗂️ Recent runs (" + runs.size() + "):\n");
        for (AgentRunRecord r : runs) {
            String req = r.getRequest() == null ? "(no prompt)" : r.getRequest().replaceAll("\\s+", " ").strip();
            if (req.length() > 80) {
                req = req.substring(0, 79) + "…";
            }
            sb.append("• ").append(r.getStatus()).append(" — \"").append(req).append("\"  [")
              .append(r.getAgent()).append("]\n");
        }
        return sb.toString().strip();
    }
}
