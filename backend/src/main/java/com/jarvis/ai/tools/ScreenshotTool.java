package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.local.MacActions;

import lombok.RequiredArgsConstructor;

/** Captures a screenshot to the Screenshots folder. */
@Component
@RequiredArgsConstructor
public class ScreenshotTool implements Tool {

    private final MacActions mac;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("screenshot", "Capture a screenshot to the Screenshots folder.",
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"optional file name\"}}}");
    }

    @Override
    public String execute(String args) {
        try { return mac.screenshot(ToolArgs.str(mapper, args, "name")); }
        catch (Exception e) { return "Error: " + e.getMessage(); }
    }
}
