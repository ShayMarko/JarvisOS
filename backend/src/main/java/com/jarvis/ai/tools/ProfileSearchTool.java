package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.profile.ProfileService;

import lombok.RequiredArgsConstructor;

/**
 * Pulls the relevant parts of the user's "About Me" profile on demand — so Jarvis can
 * recall personal details ONLY when a question is actually about the user, instead of
 * carrying the (possibly long) profile in every prompt. Token-efficient by design.
 */
@Component
@RequiredArgsConstructor
public class ProfileSearchTool implements Tool {

    private static final int MAX_CHARS = 2800;

    private final ProfileService profile;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("profile_search",
                "Recall details about the USER from their About-Me profile. Call this whenever the "
                + "question is personal (their name, preferences, projects, history, goals). Pass what "
                + "you're looking for as the query; returns the most relevant snippets.",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\","
                + "\"description\":\"What to recall about the user, e.g. 'dog name', 'job', 'preferences'.\"}},"
                + "\"required\":[\"query\"]}");
    }

    @Override
    public String execute(String args) {
        String query = ToolArgs.str(mapper, args, "query");
        try {
            return profile.search(query, MAX_CHARS);
        } catch (Exception e) {
            return "Could not read the profile: " + e.getMessage();
        }
    }
}
