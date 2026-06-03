package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.local.MacActions;

import lombok.RequiredArgsConstructor;

/** Opens a macOS application by name. */
@Component
@RequiredArgsConstructor
public class OpenAppTool implements Tool {

    private final MacActions mac;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("open_app", "Open a macOS application by name.",
                "{\"type\":\"object\",\"properties\":{\"app\":{\"type\":\"string\"}},\"required\":[\"app\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String args) {
        try { return mac.openApp(ToolArgs.str(mapper, args, "app")); }
        catch (Exception e) { return "Error: " + e.getMessage(); }
    }
}
