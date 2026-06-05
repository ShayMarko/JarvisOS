package com.jarvis.ai.tools;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.ai.VisionService;
import com.jarvis.common.MimeTypes;
import com.jarvis.explorer.FileSystemService;

import lombok.RequiredArgsConstructor;

/** Looks at an image file and answers a question about it (uses a vision-capable model). */
@Component
@RequiredArgsConstructor
public class DescribeImageTool implements Tool {

    private final FileSystemService fs;
    private final VisionService vision;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("describe_image",
                "Look at an image file (screenshot, photo, diagram) and answer a question about it, e.g. "
                + "'what error is shown', 'describe this UI'. Provide 'path' (under the Explorer) and optional 'question'.",
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"question\":{\"type\":\"string\"}},"
                + "\"required\":[\"path\"]}");
    }

    @Override
    public String execute(String args) {
        String path = ToolArgs.firstStr(mapper, args, "path", "file", "image");
        String question = ToolArgs.firstStr(mapper, args, "question", "ask", "prompt");
        if (path.isBlank()) {
            return "Error: provide the image 'path'.";
        }
        try {
            Path p = fs.resolveExisting(path);
            byte[] bytes = Files.readAllBytes(p);
            return vision.describe(bytes, MimeTypes.of(p.getFileName().toString()), question);
        } catch (Exception e) {
            return "Error reading image: " + e.getMessage();
        }
    }
}
