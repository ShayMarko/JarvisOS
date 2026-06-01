package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.local.MacActions;

import lombok.RequiredArgsConstructor;

/** Searches the Mac with Spotlight (mdfind) by name. */
@Component
@RequiredArgsConstructor
public class SpotlightTool implements Tool {

    private final MacActions mac;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("spotlight_search", "Search the Mac with Spotlight (mdfind) by name.",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}");
    }

    @Override
    public String execute(String args) {
        try { return mac.spotlight(ToolArgs.str(mapper, args, "query")); }
        catch (Exception e) { return "Error: " + e.getMessage(); }
    }
}
