package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.timeline.TimelineEntry;
import com.jarvis.timeline.TimelineService;

import lombok.RequiredArgsConstructor;

/**
 * Recalls the episodic timeline — "what did I do / decide last week". Returns a per-day roll-up of
 * activity for the last N days (default 7), so the agent can answer questions about the recent past.
 */
@Component
@RequiredArgsConstructor
public class TimelineRecallTool implements Tool {

    private final TimelineService timeline;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("timeline_recall",
                "Recall what happened over the recent past (a per-day activity roll-up). Use for "
                + "'what did I do this week', 'what happened yesterday'. Optional 'days' (default 7).",
                "{\"type\":\"object\",\"properties\":{\"days\":{\"type\":\"integer\"}}}");
    }

    @Override
    public String execute(String args) {
        int days = parseDays(ToolArgs.firstStr(mapper, args, "days", "range"));
        StringBuilder sb = new StringBuilder("🕗 Timeline (last ").append(days).append(" day(s)):\n");
        for (TimelineEntry e : timeline.recent(days)) {
            sb.append("\n").append(e.getDay()).append(" — ").append(e.getSummary());
        }
        return sb.toString().trim();
    }

    private static int parseDays(String s) {
        try {
            return s == null || s.isBlank() ? 7 : Math.max(1, Math.min(60, Integer.parseInt(s.trim())));
        } catch (NumberFormatException e) {
            return 7;
        }
    }
}
