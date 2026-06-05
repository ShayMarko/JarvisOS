package com.jarvis.ai.tools;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.document.DocumentService;

import lombok.RequiredArgsConstructor;

/**
 * Generates a self-contained marketing landing page (hero + features + pricing + CTA) for a product —
 * the "list it" step of the money loop. Deterministic HTML, no AI cost.
 */
@Component
@RequiredArgsConstructor
public class CreateLandingPageTool implements Tool {

    private final DocumentService documents;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("create_landing_page",
                "Generate a polished marketing landing page (index.html) for a product: hero, feature grid, "
                + "pricing, call-to-action. Use when listing/selling something. Provide 'title', 'tagline', "
                + "'features' (array of strings), 'price', 'cta', and optional 'folder' (e.g. Projects/<app>) "
                + "to write it alongside the product.",
                "{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"},\"tagline\":{\"type\":\"string\"},"
                + "\"features\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"price\":{\"type\":\"string\"},"
                + "\"cta\":{\"type\":\"string\"},\"folder\":{\"type\":\"string\"}},\"required\":[\"title\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            JsonNode root = ToolArgs.root(mapper, argumentsJson);
            String title = text(root, "title", "");
            if (title.isBlank()) {
                return "Provide a 'title' for the landing page.";
            }
            List<String> features = new ArrayList<>();
            JsonNode fs = root.get("features");
            if (fs != null && fs.isArray()) {
                fs.forEach(f -> { if (!f.asText("").isBlank()) features.add(f.asText()); });
            }
            String folder = text(root, "folder", "");
            String filename = folder.isBlank() ? title : "index";
            String path = documents.createLandingPage(folder, filename, title, text(root, "tagline", ""),
                    features, text(root, "price", ""), text(root, "cta", ""));
            return "Wrote the landing page to " + path + " — open it in a browser to preview.";
        } catch (Exception e) {
            return "Couldn't build the landing page: " + e.getMessage();
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? fallback : v.asText();
    }
}
