package com.jarvis.ai.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.audit.AuditLogEntry;
import com.jarvis.audit.AuditService;

import lombok.RequiredArgsConstructor;

/** Reads the recent activity/audit log — so "what's Jarvis been doing / recent activity" works anywhere. */
@Component
@RequiredArgsConstructor
public class ActivityLogTool implements Tool {

    private final AuditService audit;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("activity_log",
                "List recent audited activity (the action and whether it succeeded). Use when asked "
                + "'what have you been doing', 'recent activity', 'show the activity log'. Optional 'limit' (default 12).",
                "{\"type\":\"object\",\"properties\":{\"limit\":{\"type\":\"integer\"}}}");
    }

    @Override
    public String execute(String argumentsJson) {
        int limit = Math.max(1, Math.min(50, ToolArgs.intVal(mapper, argumentsJson, "limit", 12)));
        List<AuditLogEntry> entries = audit.recent(limit);
        if (entries.isEmpty()) {
            return "Nothing logged yet.";
        }
        StringBuilder sb = new StringBuilder("📜 Recent activity (" + entries.size() + "):\n");
        for (AuditLogEntry e : entries) {
            String label = e.getCommand() != null && !e.getCommand().isBlank() ? e.getCommand() : e.getInputType();
            sb.append("• ").append(e.getStatus()).append(' ').append(label).append('\n');
        }
        return sb.toString().strip();
    }
}
