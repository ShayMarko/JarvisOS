package com.jarvis.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.ai.VisionService;
import com.jarvis.explorer.FileSystemService;

import lombok.RequiredArgsConstructor;

/** Vision — describe/answer a question about an image file under the Explorer (surfaces describe_image). */
@RestController
@RequestMapping("/api/vision")
@RequiredArgsConstructor
public class VisionController {

    private final FileSystemService fs;
    private final VisionService vision;

    public record DescribeRequest(String path, String question) {}

    @PostMapping("/describe")
    public Map<String, Object> describe(@RequestBody DescribeRequest req) {
        if (req == null || req.path() == null || req.path().isBlank()) {
            return Map.of("result", "Provide the image 'path' (under the Explorer).");
        }
        try {
            Path p = fs.resolveExisting(req.path());
            byte[] bytes = Files.readAllBytes(p);
            String out = vision.describe(bytes, mimeOf(p.getFileName().toString()), req.question());
            return Map.of("result", out);
        } catch (Exception e) {
            return Map.of("result", "Error reading image: " + e.getMessage());
        }
    }

    private static String mimeOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        return "image/png";
    }
}
