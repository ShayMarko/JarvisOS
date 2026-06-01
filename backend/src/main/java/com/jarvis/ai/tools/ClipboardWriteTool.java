package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.local.MacActions;

import lombok.RequiredArgsConstructor;

/** Writes text to the macOS clipboard. */
@Component
@RequiredArgsConstructor
public class ClipboardWriteTool implements Tool {

    private final MacActions mac;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("clipboard_write", "Write text to the macOS clipboard.",
                "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}");
    }

    @Override
    public String execute(String args) {
        try { return mac.clipboardWrite(ToolArgs.str(mapper, args, "text")); }
        catch (Exception e) { return "Error: " + e.getMessage(); }
    }
}
