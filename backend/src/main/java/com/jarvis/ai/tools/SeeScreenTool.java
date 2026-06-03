package com.jarvis.ai.tools;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.ai.VisionService;
import com.jarvis.explorer.FileSystemService;
import com.jarvis.local.MacActions;

import lombok.RequiredArgsConstructor;

/** Captures the screen and looks at it — "what's on my screen", "read this error" (uses a vision model). */
@Component
@RequiredArgsConstructor
public class SeeScreenTool implements Tool {

    private final MacActions mac;
    private final FileSystemService fs;
    private final VisionService vision;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("see_screen",
                "Capture the current screen and answer a question about it, e.g. 'what's on my screen', "
                + "'what error is shown'. Optional 'question'.",
                "{\"type\":\"object\",\"properties\":{\"question\":{\"type\":\"string\"}}}");
    }

    @Override
    public boolean mutates() {
        return true;   // takes a screenshot (writes a file)
    }

    @Override
    public String execute(String args) {
        String question = ToolArgs.firstStr(mapper, args, "question", "ask", "prompt");
        try {
            mac.screenshot("_vision");                       // → Screenshots/_vision.png
            Path shot = fs.resolveExisting("Screenshots/_vision.png");
            byte[] bytes = Files.readAllBytes(shot);
            return vision.describe(bytes, "image/png", question);
        } catch (Exception e) {
            return "Couldn't capture/read the screen: " + e.getMessage();
        }
    }
}
