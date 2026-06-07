package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.brain.GoalService;

import lombok.RequiredArgsConstructor;

/**
 * Reads or sets the business north-star / OKR — the single persistent goal the autonomous Coordinator and
 * proactive loops steer toward. Call with no args to read it; pass {@code goal} to set/replace it (include
 * key results + a target date for a real OKR).
 */
@Component
@RequiredArgsConstructor
public class NorthStarTool implements Tool {

    private final GoalService goals;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("north_star",
                "Read or set the business north-star goal / OKR that Jarvis's autonomous loops plan toward. "
                + "No args = read the current goal; pass 'goal' to set or replace it.",
                "{\"type\":\"object\",\"properties\":{\"goal\":{\"type\":\"string\","
                + "\"description\":\"The north-star + key results + target date. Omit to just read the current goal.\"}}}");
    }

    @Override
    public boolean mutates() {
        return true;   // setting a goal writes to memory; reading is harmless but the tool can write
    }

    @Override
    public String execute(String args) {
        try {
            String goal = ToolArgs.str(mapper, args, "goal");
            if (goal != null && !goal.isBlank()) {
                return "🎯 North-star set:\n" + goals.set(goal);
            }
            String current = goals.current();
            return current == null || current.isBlank()
                    ? "No north-star set yet. Pass 'goal' to define one (e.g. a target + key results + date)."
                    : "🎯 Current north-star:\n" + current;
        } catch (Exception e) {
            return "Error with north-star: " + e.getMessage();
        }
    }
}
