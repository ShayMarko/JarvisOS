package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.local.MacActions;

import lombok.RequiredArgsConstructor;

/** Speaks text aloud on the Mac (text-to-speech). */
@Component
@RequiredArgsConstructor
public class SayTool implements Tool {

    private final MacActions mac;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("say", "Speak text aloud on the Mac (text-to-speech).",
                "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}");
    }

    @Override
    public String execute(String args) {
        try { return mac.say(ToolArgs.str(mapper, args, "text")); }
        catch (Exception e) { return "Error: " + e.getMessage(); }
    }
}
