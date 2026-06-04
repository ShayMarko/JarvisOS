package com.jarvis.ai.tools;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.ToolSpec;
import com.jarvis.document.DocumentService;

import lombok.RequiredArgsConstructor;

/**
 * Generate an image from a text prompt via OpenAI's image API (gpt-image-1) and save the PNG to the
 * Generated folder. KEY-GATED: it only fires when an OpenAI API key is configured (it costs real money
 * per image), and returns a clear message otherwise — never a silent failure.
 */
@Component
@RequiredArgsConstructor
public class GenerateImageTool implements Tool {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final String ENDPOINT = "https://api.openai.com/v1/images/generations";

    private final JarvisAiProperties props;
    private final DocumentService documents;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("generate_image",
                "Generate an image from a text prompt and save it as a PNG in the Generated folder. Use for "
                + "'make/draw/generate an image of …'. Requires an OpenAI API key (costs money per image). "
                + "Provide 'prompt'; optional 'size' (1024x1024 | 1024x1536 | 1536x1024) and 'filename'.",
                "{\"type\":\"object\",\"properties\":{\"prompt\":{\"type\":\"string\"},"
                + "\"size\":{\"type\":\"string\"},\"filename\":{\"type\":\"string\"}},\"required\":[\"prompt\"]}");
    }

    @Override
    public boolean mutates() {
        return true;   // writes a file + spends money
    }

    @Override
    public String execute(String argumentsJson) {
        String key = props.getOpenaiApiKey();
        if (key == null || key.isBlank()) {
            return "Image generation needs an OpenAI API key. Add one in Settings / the Secrets Vault "
                    + "(jarvis.ai.openai-api-key), then try again.";
        }
        try {
            JsonNode root = mapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            String prompt = text(root, "prompt", "");
            if (prompt.isBlank()) {
                return "Provide a 'prompt' describing the image to generate.";
            }
            String size = text(root, "size", "1024x1024");
            String filename = text(root, "filename", "image");

            String body = mapper.writeValueAsString(java.util.Map.of(
                    "model", "gpt-image-1", "prompt", prompt, "size", size, "n", 1));
            HttpRequest req = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                return "Image API error (HTTP " + resp.statusCode() + "): " + clip(resp.body());
            }
            JsonNode first = mapper.readTree(resp.body()).path("data").path(0);
            byte[] png;
            if (first.hasNonNull("b64_json")) {
                png = Base64.getDecoder().decode(first.get("b64_json").asText());
            } else if (first.hasNonNull("url")) {
                png = HTTP.send(HttpRequest.newBuilder(URI.create(first.get("url").asText()))
                        .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofByteArray()).body();
            } else {
                return "The image API returned no image data.";
            }
            String path = documents.saveGenerated(filename, "png", png);
            return "Generated the image and saved it to " + path + ".";
        } catch (Exception e) {
            return "Couldn't generate the image: " + e.getMessage();
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? fallback : v.asText();
    }

    private static String clip(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
