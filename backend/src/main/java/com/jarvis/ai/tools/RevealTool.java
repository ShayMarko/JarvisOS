package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.explorer.FileSystemService;
import com.jarvis.local.MacActions;

import lombok.RequiredArgsConstructor;

/** Reveals a Jarvis Explorer file in Finder. */
@Component
@RequiredArgsConstructor
public class RevealTool implements Tool {

    private final MacActions mac;
    private final FileSystemService fs;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("reveal_in_finder", "Reveal a Jarvis Explorer file in Finder.",
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}");
    }

    @Override
    public String execute(String args) {
        try { return mac.reveal(fs.resolveExisting(ToolArgs.str(mapper, args, "path"))); }
        catch (Exception e) { return "Error: " + e.getMessage(); }
    }
}
