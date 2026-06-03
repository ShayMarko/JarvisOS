package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.profile.ProfileService;

import lombok.RequiredArgsConstructor;

/**
 * Appends a durable fact to the user's "About Me" profile (under "What Jarvis has learned"),
 * so Jarvis remembers who the user is across conversations. Use for stable facts about the
 * person (their name, role, preferences) — not transient task notes.
 */
@Component
@RequiredArgsConstructor
public class UpdateProfileTool implements Tool {

    private final ProfileService profile;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("update_profile",
                "Save a durable fact about the user to their About-Me profile (e.g. their name, role, "
                + "preferences). Use only for stable facts about the person, not task details.",
                "{\"type\":\"object\",\"properties\":{\"fact\":{\"type\":\"string\","
                + "\"description\":\"A concise fact about the user to remember.\"}},\"required\":[\"fact\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String args) {
        String fact = ToolArgs.str(mapper, args, "fact");
        if (fact.isBlank()) {
            return "No fact provided.";
        }
        profile.appendLearned(fact);
        return "Saved to your profile: " + fact;
    }
}
