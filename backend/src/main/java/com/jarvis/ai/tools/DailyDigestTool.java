package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;
import com.jarvis.digest.DigestService;

import lombok.RequiredArgsConstructor;

/** Produces the "Jarvis Today" briefing: calendar, approvals, tasks and recent activity. */
@Component
@RequiredArgsConstructor
public class DailyDigestTool implements Tool {

    private final DigestService digest;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("daily_digest",
                "Get today's briefing: calendar, pending approvals, active/recent tasks and recent activity.",
                "{\"type\":\"object\",\"properties\":{}}");
    }

    @Override
    public String execute(String args) {
        try {
            return digest.build();
        } catch (Exception e) {
            return "Error building digest: " + e.getMessage();
        }
    }
}
