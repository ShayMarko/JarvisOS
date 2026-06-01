package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;
import com.jarvis.local.MacActions;

import lombok.RequiredArgsConstructor;

/** Reads the macOS clipboard contents. */
@Component
@RequiredArgsConstructor
public class ClipboardReadTool implements Tool {

    private final MacActions mac;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("clipboard_read", "Read the macOS clipboard contents.",
                "{\"type\":\"object\",\"properties\":{}}");
    }

    @Override
    public String execute(String args) {
        try { return mac.clipboardRead(); }
        catch (Exception e) { return "Error: " + e.getMessage(); }
    }
}
