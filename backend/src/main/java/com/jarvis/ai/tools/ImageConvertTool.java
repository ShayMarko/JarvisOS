package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.local.MacActions;

import lombok.RequiredArgsConstructor;

/** Converts an Explorer image to another format (jpeg/png/…) via sips. */
@Component
@RequiredArgsConstructor
public class ImageConvertTool implements Tool {

    private final MacActions mac;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("image_convert", "Convert an Explorer image to another format (jpeg/png/…) via sips.",
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"format\":{\"type\":\"string\"}},\"required\":[\"path\",\"format\"]}");
    }

    @Override
    public String execute(String args) {
        try { return mac.convertImage(ToolArgs.str(mapper, args, "path"), ToolArgs.str(mapper, args, "format")); }
        catch (Exception e) { return "Error: " + e.getMessage(); }
    }
}
