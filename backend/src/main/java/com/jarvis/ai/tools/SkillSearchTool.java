package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.skill.SkillService;

import lombok.RequiredArgsConstructor;

/**
 * Recalls a previously-taught skill's step-by-step instructions so the agent can follow them. The
 * agent calls this when a request matches a learned skill (it's told which skills exist via a compact
 * roster in its context), then performs the steps with its existing tools.
 */
@Component
@RequiredArgsConstructor
public class SkillSearchTool implements Tool {

    private final SkillService skills;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("skill_search",
                "Recall how to perform a skill you were taught. Pass what you're trying to do as 'query'; "
                + "returns the step-by-step instructions to follow.",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}");
    }

    @Override
    public String execute(String args) {
        String query = ToolArgs.firstStr(mapper, args, "query", "skill", "name", "task");
        if (query.isBlank()) {
            return "Provide a 'query' describing the skill you want to recall.";
        }
        String instructions = skills.findInstructions(query);
        return instructions == null ? "No learned skill matches that — do it from scratch (and consider learn_skill if it's worth keeping)." : instructions;
    }
}
